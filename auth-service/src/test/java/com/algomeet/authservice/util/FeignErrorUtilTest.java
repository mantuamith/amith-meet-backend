package com.algomeet.authservice.util;

import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeignErrorUtilTest {

  // --- helpers -------------------------------------------------------------

  private static FeignException feignWithBody(int status, String body) {
    // minimal request Feign requires (method, url, headers, body, charset, template)
    Request req = Request.create(
            Request.HttpMethod.GET,
            "http://example.com/test",
            Collections.emptyMap(),   // Map<String, Collection<String>>
            null,                     // request body
            StandardCharsets.UTF_8,
            null                      // RequestTemplate
    );

    // headers map for the FeignException constructor
    Map<String, Collection<String>> headers = Collections.emptyMap();

    return new FeignException.BadRequest(
            "HTTP " + status,
            req,
            body == null ? null : body.getBytes(StandardCharsets.UTF_8),
            headers
    );
  }

  // --- extractDuplicateFields ---------------------------------------------

  @Test
  void extractDuplicateFields_returnsParsedSet_whenFieldsArrayPresent() {
    String json = """
          {
            "message":"Duplicate",
            "error":"Conflict",
            "fields":["email","username","email"]
          }
        """;
    var ex = feignWithBody(409, json);

    var fields = FeignErrorUtil.extractDuplicateFields(ex);

    assertThat(fields)
            .containsExactlyInAnyOrder("email", "username");
  }

  @Test
  void extractDuplicateFields_returnsEmpty_whenNoFieldsKey() {
    String json = """
          {"message":"Conflict","error":"Conflict","code":"USER_DUP"}
        """;
    var ex = feignWithBody(409, json);

    assertThat(FeignErrorUtil.extractDuplicateFields(ex)).isEmpty();
  }

  @Test
  void extractDuplicateFields_returnsEmpty_whenBodyIsNotJson_orNull() {
    assertThat(FeignErrorUtil.extractDuplicateFields(feignWithBody(400, "plain-text"))).isEmpty();
    assertThat(FeignErrorUtil.extractDuplicateFields(feignWithBody(400, null))).isEmpty();
  }

  // --- extractCode ---------------------------------------------------------

  @Test
  void extractCode_returnsCode_whenPresent() {
    String json = """
          {"message":"Boom","error":"BadRequest","path":"/x","code":"E123"}
        """;
    var ex = feignWithBody(400, json);

    assertThat(FeignErrorUtil.extractCode(ex)).isEqualTo("E123");
  }

  @Test
  void extractCode_returnsNull_whenMissing_orNonJson_orNull() {
    assertThat(FeignErrorUtil.extractCode(feignWithBody(400, """
          {"message":"Boom","error":"BadRequest"}
        """))).isNull();

    assertThat(FeignErrorUtil.extractCode(feignWithBody(400, "not-json"))).isNull();
    assertThat(FeignErrorUtil.extractCode(feignWithBody(400, null))).isNull();
  }

  @Test
  void extractDuplicateFields_emptyArray_returnsEmptySet() {
    String json = """
      {"message":"dup","error":"Conflict","fields":[]}
      """;
    FeignException ex = feignWithBody(409, json);

    assertThat(FeignErrorUtil.extractDuplicateFields(ex)).isEmpty();
  }

  @Test
  void extractCode_missingKey_returnsNull() {
    String json = """
      {"message":"oops","error":"BadRequest"}
      """;
    FeignException ex = feignWithBody(400, json);

    assertThat(FeignErrorUtil.extractCode(ex)).isNull();
  }
}
