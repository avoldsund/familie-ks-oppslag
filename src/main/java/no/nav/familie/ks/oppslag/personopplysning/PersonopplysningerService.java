package no.nav.familie.ks.oppslag.personopplysning;

import no.nav.familie.ks.oppslag.aktør.AktørService;
import no.nav.familie.ks.oppslag.personopplysning.domene.AktørId;
import no.nav.familie.ks.oppslag.personopplysning.domene.PersonhistorikkInfo;
import no.nav.familie.ks.oppslag.personopplysning.domene.Personinfo;
import no.nav.familie.ks.oppslag.personopplysning.domene.TpsOversetter;
import no.nav.familie.ks.oppslag.personopplysning.internal.PersonConsumer;
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonPersonIkkeFunnet;
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonSikkerhetsbegrensning;
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonhistorikkPersonIkkeFunnet;
import no.nav.tjeneste.virksomhet.person.v3.binding.HentPersonhistorikkSikkerhetsbegrensning;
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Informasjonsbehov;
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent;
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent;
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonRequest;
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonResponse;
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonhistorikkRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.ApplicationScope;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;


@Service
@ApplicationScope
public class PersonopplysningerService {

    private static final Logger LOG = LoggerFactory.getLogger(PersonopplysningerService.class);
    private final PersonConsumer personConsumer;
    private AktørService aktørService;
    private TpsOversetter oversetter;

    @Autowired
    public PersonopplysningerService(AktørService aktørService, PersonConsumer personConsumer, TpsOversetter oversetter) {
        this.aktørService = aktørService;
        this.personConsumer = personConsumer;
        this.oversetter = oversetter;
    }

    public PersonhistorikkInfo hentHistorikkFor(AktørId aktørId, LocalDate fom, LocalDate tom) {
        Objects.requireNonNull(aktørId, "aktørId");
        Objects.requireNonNull(fom, "fom");
        Objects.requireNonNull(tom, "tom");
        final var personIdent = aktørService.getPersonIdent(aktørId);
        var request = new HentPersonhistorikkRequest();
        request.setAktoer(new PersonIdent().withIdent(new NorskIdent().withIdent(personIdent)));
        try {
            var response = personConsumer.hentPersonhistorikkResponse(request);
            return oversetter.tilPersonhistorikkInfo(aktørId.getId(), response);
        } catch (HentPersonhistorikkSikkerhetsbegrensning hentPersonhistorikkSikkerhetsbegrensning) {
            LOG.info("Ikke tilgang til å hente historikk for person");
            throw new IllegalArgumentException(hentPersonhistorikkSikkerhetsbegrensning);
        } catch (HentPersonhistorikkPersonIkkeFunnet hentPersonhistorikkPersonIkkeFunnet) {
            // Fant ikke personen returnerer tomt sett
            LOG.info("Prøver å hente historikk for person som ikke finnes i TPS");
            throw new IllegalArgumentException(hentPersonhistorikkPersonIkkeFunnet);
        }
    }

    public Personinfo hentPersoninfoFor(AktørId aktørId) {
        Objects.requireNonNull(aktørId, "aktørId");
        final var personIdent = aktørService.getPersonIdent(aktørId);
        HentPersonRequest request = new HentPersonRequest()
                .withAktoer(new PersonIdent().withIdent(new NorskIdent().withIdent(personIdent)))
                .withInformasjonsbehov(List.of(Informasjonsbehov.FAMILIERELASJONER, Informasjonsbehov.ADRESSE));
        try {
            HentPersonResponse response = personConsumer.hentPersonResponse(request);
            return oversetter.tilPersoninfo(aktørId, response);
        } catch (HentPersonSikkerhetsbegrensning hentPersonSikkerhetsbegrensning) {
            LOG.info("Ikke tilgang til å hente personinfo for person");
            throw new IllegalArgumentException(hentPersonSikkerhetsbegrensning);
        } catch (HentPersonPersonIkkeFunnet hentPersonPersonIkkeFunnet) {
            LOG.info("Prøver å hente personinfo for person som ikke finnes i TPS");
            throw new IllegalArgumentException(hentPersonPersonIkkeFunnet);
        }
    }
}
