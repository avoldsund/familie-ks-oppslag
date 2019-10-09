package no.nav.familie.ks.oppslag.dokarkiv;

import no.nav.familie.ks.kontrakter.dokarkiv.api.ArkiverDokumentRequest;
import no.nav.familie.ks.kontrakter.dokarkiv.api.Dokument;
import no.nav.familie.ks.kontrakter.dokarkiv.api.DokumentType;
import no.nav.familie.ks.kontrakter.dokarkiv.api.FilType;
import no.nav.familie.ks.oppslag.OppslagSpringRunnerTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;

@ActiveProfiles(profiles = {"dev", "mock-sts", "mock-aktor", "mock-personopplysninger"})
public class DokarkivControllerTest extends OppslagSpringRunnerTest {
    public static final int MOCK_SERVER_PORT = 18321;
    public static final String DOKARKIV_URL = "/api/arkiv/";
    public static final Dokument HOVEDDOKUMENT = new Dokument("foo".getBytes(), FilType.PDFA, "filnavn", DokumentType.KONTANTSTØTTE_SØKNAD);
    public static final Dokument VEDLEGG = new Dokument("foo".getBytes(), FilType.PDFA, "filnavn", DokumentType.KONTANTSTØTTE_SØKNAD_VEDLEGG);
    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this, MOCK_SERVER_PORT);

    @Before
    public void setUp() {
        headers.setBearerAuth(getLokalTestToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
    }

    @Test
    public void skal_returnere_Bad_Request_hvis_fNr_mangler() {
        String request = "{ }";

        ResponseEntity<String> response = restTemplate.exchange(
                localhost(DOKARKIV_URL), HttpMethod.POST, new HttpEntity<String>(request, headers), String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(response.getBody()).contains("value failed for JSON property fnr due to missing");
    }


    @Test
    public void skal_returnere_Bad_Request_hvis_fNr_blank() {
        String request = "{ \"fnr\": \"\", \"dokumenter\": []}";

        ResponseEntity<String> response = restTemplate.exchange(
                localhost(DOKARKIV_URL), HttpMethod.POST, new HttpEntity<String>(request, headers), String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(response.getBody()).contains("fnr\":\"must not be blank");
    }

    @Test
    public void skal_returnere_Bad_Request_hvis_ingen_dokumenter() {
        ArkiverDokumentRequest body = new ArkiverDokumentRequest("fnr", false, new LinkedList<>());

        ResponseEntity<String> response = restTemplate.exchange(
                localhost(DOKARKIV_URL), HttpMethod.POST, new HttpEntity<ArkiverDokumentRequest>(body, headers), String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(response.getBody()).contains("dokumenter\":\"must not be empty");
    }

    @Test
    public void skal_midlertidig_journalføre_dokument() throws IOException {
        mockServerRule.getClient()
                .when(
                        HttpRequest
                                .request()
                                .withMethod("POST")
                                .withPath("/rest/journalpostapi/v1/journalpost")
                                .withQueryStringParameter("foersoekFerdigstill", "false")
                )
                .respond(
                        HttpResponse.response().withBody(gyldigDokarkivResponse())
                );


        ArkiverDokumentRequest body = new ArkiverDokumentRequest("FNR", false, List.of(HOVEDDOKUMENT));
        ResponseEntity<String> response = restTemplate.exchange(
                localhost(DOKARKIV_URL), HttpMethod.POST, new HttpEntity<>(body, headers), String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody()).isEqualTo("{\"journalpostId\":\"12345678\",\"ferdigstilt\":false}");
    }

    @Test
    public void skal_midlertidig_journalføre_dokument_med_vedlegg() throws IOException {
        mockServerRule.getClient()
                .when(
                        HttpRequest
                                .request()
                                .withMethod("POST")
                                .withPath("/rest/journalpostapi/v1/journalpost")
                                .withQueryStringParameter("foersoekFerdigstill", "false")
                )
                .respond(
                        HttpResponse.response().withBody(gyldigDokarkivResponse())
                );


        ArkiverDokumentRequest body = new ArkiverDokumentRequest("FNR", false, List.of(HOVEDDOKUMENT, VEDLEGG));
        ResponseEntity<String> response = restTemplate.exchange(
                localhost(DOKARKIV_URL), HttpMethod.POST, new HttpEntity<>(body, headers), String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
        assertThat(response.getBody()).isEqualTo("{\"journalpostId\":\"12345678\",\"ferdigstilt\":false}");
    }

    @Test
    public void dokarkiv_returnerer_401() throws IOException {
        mockServerRule.getClient()
                .when(
                        HttpRequest
                                .request()
                                .withMethod("POST")
                                .withPath("/rest/journalpostapi/v1/journalpost")
                                .withQueryStringParameter("foersoekFerdigstill", "false")
                )
                .respond(
                        HttpResponse.response().withStatusCode(401)
                );


        ArkiverDokumentRequest body = new ArkiverDokumentRequest("FNR", false, List.of(new Dokument("foo".getBytes(), FilType.PDFA, null, DokumentType.KONTANTSTØTTE_SØKNAD)));
        ResponseEntity<String> response = restTemplate.exchange(
                localhost(DOKARKIV_URL), HttpMethod.POST, new HttpEntity<>(body, headers), String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String gyldigDokarkivResponse() throws IOException {
        return Files.readString(new ClassPathResource("dokarkiv/gyldigresponse.json").getFile().toPath(), StandardCharsets.UTF_8);
    }
}
