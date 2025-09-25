package com.kitchensink.core.member.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class MemberFilterRequest {
    // paging
    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int size = 10;

    // sorting (allow one or many fields)
    @Builder.Default
    private List<String> sortBy = List.of("name");
    @Builder.Default
    private String dir = "asc"; // asc|desc (applies to all sortBy fields)

    // filters (all optional)
    private String q;            // free-text across name/email/phone/place
    private String name;
    private String email;
    private String phoneNumber;
    private String place;
    private int ageMin;
    private int ageMax;
    private LocalDate registrationDate;       // exact
    private LocalDate registrationDateFrom;   // range start (inclusive)
    private LocalDate registrationDateTo;   // range end   (inclusive)
}
