package com.quickstarts.kitchensink.service;

import com.quickstarts.kitchensink.dto.MemberFilterRequest;
import com.quickstarts.kitchensink.model.Member;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.quickstarts.kitchensink.constants.Constants.EXPORT_EXCEL_SHEET_CONTENT_TYPE;
import static com.quickstarts.kitchensink.utils.MemberUtils.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.LocalDate.now;
import static java.util.Set.of;
import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.domain.Sort.Direction.DESC;

@Service
@RequiredArgsConstructor
public class MemberExportService {

    private final MongoTemplate mongoTemplate;

    /* ---------------- CSV ---------------- */
    public void exportCsv(final MemberFilterRequest filterRequest, final HttpServletResponse response) throws IOException {
        final var query = buildQueryForExport(filterRequest);
        final var filename = "members-" + now() + ".csv";
        response.setCharacterEncoding(UTF_8.name());
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        try (var out = response.getOutputStream();
             var w = new BufferedWriter(new OutputStreamWriter(out, UTF_8));
             Stream<Member> stream = mongoTemplate.stream(query, Member.class)) {

            // header
            w.write("Registration Date,Name,Email,Phone,Age,Place\n");

            stream.forEachOrdered(m -> {
                try {
                    writeCsvRow(w, m);
                } catch (java.io.IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            // unwrap and rethrow as IO so container handles it cleanly
            throw e.getCause();
        }
    }


    /* ---------------- Query builder (no pagination) ---------------- */

    private void writeCsvRow(final Writer writer, final Member member) throws IOException {
        final var cols = new String[]{
                formatDate(member.getRegistrationDate()),
                emptyIfNull(member.getName()),
                emptyIfNull(member.getEmail()),
                emptyIfNull(member.getPhoneNumber()),
                (member.getAge() == 0 ? "" : String.valueOf(member.getAge())),
                emptyIfNull(member.getPlace())
        };
        for (int i = 0; i < cols.length; i++) {
            String v = cols[i].replace("\"", "\"\"");
            writer.write('"');
            writer.write(v);
            writer.write('"');
            if (i < cols.length - 1) writer.write(',');
        }
        writer.write('\n');
    }

    public void exportXlsx(final MemberFilterRequest filterRequest, final HttpServletResponse httpServletResponse) throws IOException {
        final var query = buildQueryForExport(filterRequest);
        final var filename = "members-" + now() + ".xlsx";
        httpServletResponse.setContentType(EXPORT_EXCEL_SHEET_CONTENT_TYPE);
        httpServletResponse.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200)) {
            SXSSFSheet workbookSheet = workbook.createSheet("Members");
            workbookSheet.trackAllColumnsForAutoSizing();

            int r = 0;
            String[] heads = {"Registration Date", "Name", "Email", "Phone", "Age", "Place"};

            var header = workbookSheet.createRow(r++);
            for (int c = 0; c < heads.length; c++) header.createCell(c).setCellValue(heads[c]);

            try (Stream<Member> stream = mongoTemplate.stream(query, Member.class)) {
                final int[] rowIdx = {r};
                stream.forEachOrdered(m -> {
                    var row = workbookSheet.createRow(rowIdx[0]++);
                    int c = 0;
                    row.createCell(c++).setCellValue(formatDate(m.getRegistrationDate()));
                    row.createCell(c++).setCellValue(emptyIfNull(m.getName()));
                    row.createCell(c++).setCellValue(emptyIfNull(m.getEmail()));
                    row.createCell(c++).setCellValue(emptyIfNull(m.getPhoneNumber()));
                    row.createCell(c++).setCellValue(m.getAge()); // numeric is fine
                    row.createCell(c++).setCellValue(emptyIfNull(m.getPlace()));
                });
            }

            // Now it's legal to auto-size tracked columns
            for (int c = 0; c < heads.length; c++) workbookSheet.autoSizeColumn(c);

            try (var os = httpServletResponse.getOutputStream()) {
                workbook.write(os);
            }
            workbook.dispose(); // cleanup temp files
        }
    }

    private Query buildQueryForExport(final MemberFilterRequest filterRequest) {
        // sort whitelist
        var allowed = of("name", "email", "phoneNumber", "age", "place", "registrationDate");
        var sortFields = (filterRequest.getSortBy() == null || filterRequest.getSortBy().isEmpty() ? List.of("registrationDate") : filterRequest.getSortBy())
                .stream().filter(allowed::contains).toList();
        var dir = "desc".equalsIgnoreCase(filterRequest.getDir()) ? DESC : ASC;
        var sort = Sort.by(sortFields.stream().map(fn -> new Sort.Order(dir, fn)).toList());

        var q = new Query().with(sort);
        var ands = new ArrayList<Criteria>();

        // q over text fields (name/email/phone/place); registrationDate is LocalDate so skip here
        if (hasText(filterRequest.getQ())) {
            String rx = Pattern.quote(filterRequest.getQ().trim());
            ands.add(new Criteria().orOperator(
                    Criteria.where("name").regex(rx, "i"),
                    Criteria.where("email").regex(rx, "i"),
                    Criteria.where("phoneNumber").regex(rx, "i"),
                    Criteria.where("place").regex(rx, "i")
            ));
        }

        if (hasText(filterRequest.getName()))
            ands.add(Criteria.where("name").regex(Pattern.quote(filterRequest.getName().trim()), "i"));
        if (hasText(filterRequest.getEmail()))
            ands.add(Criteria.where("email").regex(Pattern.quote(filterRequest.getEmail().trim()), "i"));
        if (hasText(filterRequest.getPhoneNumber()))
            ands.add(Criteria.where("phoneNumber").regex(Pattern.quote(filterRequest.getPhoneNumber().trim()), "i"));
        if (hasText(filterRequest.getPlace()))
            ands.add(Criteria.where("place").regex(Pattern.quote(filterRequest.getPlace().trim()), "i"));
        if (filterRequest.getAgeMin() != 0) ands.add(Criteria.where("age").gte(filterRequest.getAgeMin()));
        if (filterRequest.getAgeMax() != 0) ands.add(Criteria.where("age").lte(filterRequest.getAgeMax()));

        // LocalDate filters: exact / from / to
        if (filterRequest.getRegistrationDate() != null) {
            ands.add(Criteria.where("registrationDate").is(filterRequest.getRegistrationDate()));
        } else {
            if (filterRequest.getRegistrationDateFrom() != null)
                ands.add(Criteria.where("registrationDate").gte(filterRequest.getRegistrationDateFrom()));
            if (filterRequest.getRegistrationDateTo() != null)
                ands.add(Criteria.where("registrationDate").lte(filterRequest.getRegistrationDateTo()));
        }

        if (!ands.isEmpty()) q.addCriteria(new Criteria().andOperator(ands.toArray(Criteria[]::new)));
        return q;
    }
}
