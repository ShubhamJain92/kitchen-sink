package com.quickstarts.kitchensink.service;

import com.quickstarts.kitchensink.dto.MemberFilterRequest;
import com.quickstarts.kitchensink.model.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.quickstarts.kitchensink.utils.MemberUtils.hasText;
import static com.quickstarts.kitchensink.utils.MemberUtils.tryParseLocalDate;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.Math.clamp;
import static java.util.Objects.requireNonNullElse;
import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.domain.Sort.Direction.DESC;

@Service
@RequiredArgsConstructor
public class MemberQueryService {

    private final Set<String> allowedColumns = Set.of("name", "email", "phoneNumber", "age", "place", "registrationDate");
    private final MongoTemplate mongoTemplate;

    public Page<Member> searchMembers(final MemberFilterRequest filterRequest) {
        final var page = clamp(requireNonNullElse(filterRequest.getPage(), 0), 0, MAX_VALUE);
        final var size = clamp(requireNonNullElse(filterRequest.getSize(), 10), 1, 100);
        final var fields = ((filterRequest.getSortBy() == null || filterRequest.getSortBy().isEmpty())
                ? List.of("registrationDate") : filterRequest.getSortBy())
                .stream().filter(allowedColumns::contains).toList();

        final var direction = "desc".equalsIgnoreCase(filterRequest.getDir()) ? DESC : ASC;
        final var sort = Sort.by(fields.stream().map(fn -> new Sort.Order(direction, fn)).toList());
        final var pageable = PageRequest.of(page, size, sort);

        final var query = new Query().with(pageable);
        final List<Criteria> criteriaList = new ArrayList<>();

        // --- Free-text query over text fields only; optionally match exact LocalDate if query parses
        if (hasText(filterRequest.getQ())) {
            final var term = filterRequest.getQ().trim();
            final var rx = Pattern.quote(term);

            List<Criteria> ors = new ArrayList<>();
            ors.add(Criteria.where("name").regex(rx, "i"));
            ors.add(Criteria.where("email").regex(rx, "i"));
            ors.add(Criteria.where("phoneNumber").regex(rx, "i"));
            ors.add(Criteria.where("place").regex(rx, "i"));

            // if query looks like yyyy-MM-dd, include an exact date match
            LocalDate parsed = tryParseLocalDate(term);
            if (parsed != null) {
                ors.add(Criteria.where("registrationDate").is(parsed));
            }
            criteriaList.add(new Criteria().orOperator(ors.toArray(Criteria[]::new)));
        }

        // --- Field-specific filters
        if (hasText(filterRequest.getName()))
            criteriaList.add(Criteria.where("name").regex(Pattern.quote(filterRequest.getName().trim()), "i"));
        if (hasText(filterRequest.getEmail()))
            criteriaList.add(Criteria.where("email").regex(Pattern.quote(filterRequest.getEmail().trim()), "i"));
        if (hasText(filterRequest.getPhoneNumber()))
            criteriaList.add(Criteria.where("phoneNumber").regex(Pattern.quote(filterRequest.getPhoneNumber().trim()), "i"));
        if (hasText(filterRequest.getPlace()))
            criteriaList.add(Criteria.where("place").regex(Pattern.quote(filterRequest.getPlace().trim()), "i"));
        if (filterRequest.getAgeMin() != 0) criteriaList.add(Criteria.where("age").gte(filterRequest.getAgeMin()));
        if (filterRequest.getAgeMax() != 0) criteriaList.add(Criteria.where("age").lte(filterRequest.getAgeMax()));

        // --- LocalDate filters: exact / from / to
        if (filterRequest.getRegistrationDate() != null) {
            criteriaList.add(Criteria.where("registrationDate").is(filterRequest.getRegistrationDate()));
        } else {
            if (filterRequest.getRegistrationDateFrom() != null) {
                criteriaList.add(Criteria.where("registrationDate").gte(filterRequest.getRegistrationDateFrom()));
            }
            if (filterRequest.getRegistrationDateTo() != null) {
                criteriaList.add(Criteria.where("registrationDate").lte(filterRequest.getRegistrationDateTo()));
            }
        }

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(Criteria[]::new)));
        }

        final var total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Member.class);
        final var content = mongoTemplate.find(query, Member.class);
        return new PageImpl<>(content, pageable, total);
    }
}
