package com.quickstarts.kitchensink.service;

import com.quickstarts.kitchensink.dto.MemberFilterRequest;
import com.quickstarts.kitchensink.model.Member;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.quickstarts.kitchensink.constants.Constants.EXPORT_EXCEL_SHEET_CONTENT_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberExportServiceTest {

    @Mock
    MongoTemplate mongoTemplate;

    @InjectMocks
    MemberExportService service;

    @Mock
    HttpServletResponse response;

    private static Member member(
            String name, String email, String phone, int age, String place, LocalDate registered) {
        var m = new Member();
        m.setName(name);
        m.setEmail(email);
        m.setPhoneNumber(phone);
        m.setAge(age);
        m.setPlace(place);
        m.setRegistrationDate(registered);
        return m;
    }

    private static MemberFilterRequest emptyFilter() {
        // default sorting will be by registrationDate desc inside service
        return new MemberFilterRequest();
    }

    @Nested
    class ExportCsv {

        @Test
        @DisplayName("exportCsv: writes header + rows and sets content headers")
        void exportCsv_writesHeaderAndRows() throws IOException {
            // Arrange
            var members = List.of(
                    member("Alice", "alice@example.com", "9990001111", 25, "Pune",
                            LocalDate.of(2024, 1, 15)),
                    member("Bob", "", null, 0, "Mumbai", LocalDate.of(2024, 2, 10))
            );
            when(mongoTemplate.stream(any(Query.class), eq(Member.class)))
                    .thenReturn(members.stream());

            var baos = new ByteArrayOutputStream();
            when(response.getOutputStream()).thenReturn(new TestServletOutputStream(baos));

            // Act
            service.exportCsv(emptyFilter(), response);

            // Assert servlet headers
            verify(response).setCharacterEncoding("UTF-8");
            verify(response).setContentType("text/csv; charset=UTF-8");
            ArgumentCaptor<String> cd = ArgumentCaptor.forClass(String.class);
            verify(response).setHeader(eq("Content-Disposition"), cd.capture());
            // filename format: members-<date>.csv
            assertThat(cd.getValue()).matches("attachment; filename=\"members-.*\\.csv\"");

            // Assert content
            var csv = baos.toString("UTF-8");
            String[] lines = csv.split("\\R");
            assertThat(lines).isNotEmpty();
            assertThat(lines[0]).isEqualTo("Registration Date,Name,Email,Phone,Age,Place");

            // We don’t assert the exact date format—only that values appear quoted and comma-separated.
            assertThat(csv).contains("\"Alice\"");
            assertThat(csv).contains("\"alice@example.com\"");
            assertThat(csv).contains("\"9990001111\"");
            assertThat(csv).contains("\"25\"");
            assertThat(csv).contains("\"Pune\"");

            // Bob: age==0 should render blank; email null -> empty string; phone null -> empty string
            // We assert presence of empty field patterns by checking consecutive quotes with comma between.
            assertThat(csv).contains("\"Bob\"");
            // Email empty
            assertThat(csv).contains(",\"\",\""); // email empty then phone starts
        }

        @Test
        @DisplayName("exportCsv: unwraps and rethrows IOException cleanly")
        void exportCsv_propagatesIOException() throws IOException {
            // Arrange: make the output stream throw on write to trigger IOException path
            when(mongoTemplate.stream(any(Query.class), eq(Member.class)))
                    .thenReturn(Stream.of(member("X", "x@y", null, 1, "Z", LocalDate.now())));

            var os = new ServletOutputStream() {
                @Override public boolean isReady() { return true; }
                @Override public void setWriteListener(WriteListener writeListener) {}
                @Override public void write(int b) throws IOException { throw new IOException("boom"); }
            };
            when(response.getOutputStream()).thenReturn(os);

            // Act + Assert
            assertThrows(IOException.class, () -> service.exportCsv(emptyFilter(), response));
        }

        @Test
        @DisplayName("exportCsv: passes a Query with sort to MongoTemplate.stream")
        void exportCsv_verifiesQueryHasSort() throws IOException {
            // Arrange
            when(mongoTemplate.stream(any(Query.class), eq(Member.class)))
                    .thenAnswer(inv -> Stream.empty());

            var baos = new ByteArrayOutputStream();
            when(response.getOutputStream()).thenReturn(new TestServletOutputStream(baos));

            var filter = new MemberFilterRequest();
            filter.setSortBy(List.of("registrationDate"));
            filter.setDir("desc");

            // Act
            service.exportCsv(filter, response);

            // Assert query captured
            ArgumentCaptor<Query> qCap = ArgumentCaptor.forClass(Query.class);
            verify(mongoTemplate).stream(qCap.capture(), eq(Member.class));

            Document sortDoc = qCap.getValue().getSortObject();
            assertThat(sortDoc).isNotNull();
            // Expect { "registrationDate" : -1 } for DESC
            assertThat(sortDoc.getInteger("registrationDate")).isEqualTo(-1);
        }
    }

    @Nested
    class ExportXlsx {

        @Test
        @DisplayName("exportXlsx: writes workbook with header + data and sets content headers")
        void exportXlsx_writesWorkbook_andHeaders() throws Exception {
            // Arrange
            var members = List.of(
                    member("Charlie", "c@example.com", "7778889999", 31, "Bengaluru",
                            LocalDate.of(2024, 3, 1))
            );
            when(mongoTemplate.stream(any(Query.class), eq(Member.class)))
                    .thenReturn(members.stream());

            var baos = new ByteArrayOutputStream();
            when(response.getOutputStream()).thenReturn(new TestServletOutputStream(baos));

            // Capture Content-Disposition value
            AtomicReference<String> cdRef = new AtomicReference<>();
            doAnswer(inv -> {
                if ("Content-Disposition".equals(inv.getArgument(0))) {
                    cdRef.set(inv.getArgument(1));
                }
                return null;
            }).when(response).setHeader(anyString(), anyString());

            // Act
            service.exportXlsx(emptyFilter(), response);

            // Assert headers
            verify(response).setContentType(EXPORT_EXCEL_SHEET_CONTENT_TYPE);
            assertThat(cdRef.get()).matches("attachment; filename=\"members-.*\\.xlsx\"");

            // Read the workbook back and assert contents
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(baos.toByteArray()))) {
                assertThat(wb.getNumberOfSheets()).isEqualTo(1);
                var sheet = wb.getSheet("Members");
                assertNotNull(sheet, "Sheet 'Members' should exist");

                var header = sheet.getRow(0);
                assertNotNull(header);
                assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Registration Date");
                assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Name");
                assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Email");
                assertThat(header.getCell(3).getStringCellValue()).isEqualTo("Phone");
                assertThat(header.getCell(4).getStringCellValue()).isEqualTo("Age");
                assertThat(header.getCell(5).getStringCellValue()).isEqualTo("Place");

                var row1 = sheet.getRow(1);
                assertNotNull(row1);
                assertThat(row1.getCell(1).getStringCellValue()).isEqualTo("Charlie");
                assertThat(row1.getCell(2).getStringCellValue()).isEqualTo("c@example.com");
                assertThat(row1.getCell(3).getStringCellValue()).isEqualTo("7778889999");
                assertThat((int) row1.getCell(4).getNumericCellValue()).isEqualTo(31);
                assertThat(row1.getCell(5).getStringCellValue()).isEqualTo("Bengaluru");
            }
        }
    }

    // --- helpers ---

    /**
     * Minimal ServletOutputStream writing into a backing ByteArrayOutputStream.
     */
    static class TestServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream target;
        TestServletOutputStream(ByteArrayOutputStream target) { this.target = target; }
        @Override public boolean isReady() { return true; }
        @Override public void setWriteListener(WriteListener writeListener) { /*  empty */ }
        @Override public void write(int b) {
            target.write(b);
        }
    }
}
