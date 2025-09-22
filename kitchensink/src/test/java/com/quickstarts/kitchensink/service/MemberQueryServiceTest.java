package com.quickstarts.kitchensink.service;

import com.quickstarts.kitchensink.dto.MemberFilterRequest;
import com.quickstarts.kitchensink.model.Member;
import org.bson.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberQueryServiceTest {

    @Mock
    MongoTemplate mongoTemplate;

    @Captor
    ArgumentCaptor<Query> queryCaptor;

    @Test
    void defaultPagingAndSort_whenNoFilters() {
        var svc = new MemberQueryService(mongoTemplate);

        when(mongoTemplate.count(any(Query.class), eq(Member.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(Member.class))).thenReturn(List.of());

        var memberFilterRequest = new MemberFilterRequest();
        Page<Member> page = svc.searchMembers(memberFilterRequest);

        // Verify interactions & capture the actual Query
        verify(mongoTemplate).count(queryCaptor.capture(), eq(Member.class));
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Member.class));

        // Assert page defaults
        assertEquals(0, page.getNumber());
        assertEquals(10, page.getSize());

        // Inspect captured query (last call)
        var q = queryCaptor.getAllValues().getLast();
        assertThat(q.getSkip()).isZero();
        assertThat(q.getLimit()).isEqualTo(10);

        Document sortDoc = q.getSortObject();
        assertThat(sortDoc).isNotNull();
        Assertions.assertNull(sortDoc.getInteger("registrationDate"));

        // No filters -> empty criteria
        Document crit = q.getQueryObject();
        assertThat(crit).isNotNull();
        assertTrue(crit.isEmpty());
    }

    @Test
    void freeTextBuildsOrRegex_overNameEmailPhonePlace() {
        var svc = new MemberQueryService(mongoTemplate);

        when(mongoTemplate.count(any(Query.class), eq(Member.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Member.class))).thenReturn(List.of(new Member()));

        var filterRequest = MemberFilterRequest.builder()
                .q("Virat")
                .page(2)
                .size(5)
                .dir("asc")
                .sortBy(List.of("name"))
                .ageMin(1)
                .ageMax(120)
                .build();

        Page<Member> page = svc.searchMembers(filterRequest);

        verify(mongoTemplate).count(queryCaptor.capture(), eq(Member.class));
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Member.class));

        assertThat(page.getNumber()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(5);

        var q = queryCaptor.getAllValues().getLast();
        assertThat(q.getSkip()).isEqualTo(2 * 5);
        assertThat(q.getLimit()).isEqualTo(5);

        Document sortDoc = q.getSortObject();
        assertThat(sortDoc.getInteger("name")).isEqualTo(1);

        // Criteria should include $and with an $or of the 4 regex conditions
        Document critDoc = q.getQueryObject();
        String critString = critDoc.toJson();

        assertThat(critString).contains("$and");
        assertThat(critString).contains("$or");
        assertThat(critString).contains("name");
        assertThat(critString).contains("email");
        assertThat(critString).contains("phoneNumber");
        assertThat(critString).contains("place");
        // patterns are quoted (Pattern.quote), case-insensitive ("i")
        assertThat(critString).contains("Virat");
        assertThat(critString).contains("$regularExpression");
        assertThat(critString).contains("\"options\": \"i\"");
    }
}
