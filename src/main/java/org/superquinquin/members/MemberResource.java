package org.superquinquin.members;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/members")
@Produces(MediaType.APPLICATION_JSON)
public class MemberResource {

    @Inject MemberRepository repository;
    @Inject MeterRegistry registry;

    private Counter searchCounter;
    private Counter searchEmptyQueryCounter;
    private Counter detailCounter;
    private Counter detailNotFoundCounter;

    @PostConstruct
    void initMeters() {
        searchCounter = Counter.builder("sesame.members.search")
                .description("Number of member search requests")
                .register(registry);
        searchEmptyQueryCounter = Counter.builder("sesame.members.search.empty_query")
                .description("Number of member search requests with no query")
                .register(registry);
        detailCounter = Counter.builder("sesame.members.detail")
                .description("Number of member detail requests")
                .register(registry);
        detailNotFoundCounter = Counter.builder("sesame.members.detail.not_found")
                .description("Number of member detail requests that returned 404")
                .register(registry);
    }

    @GET
    public List<MemberSummary> search(@QueryParam("q") String q) {
        searchCounter.increment();
        if (q == null || q.isBlank()) {
            searchEmptyQueryCounter.increment();
        }
        return repository.search(q);
    }

    @GET
    @Path("/{id}")
    public MemberDetail detail(@PathParam("id") int id) {
        detailCounter.increment();
        return repository.findById(id)
                .orElseThrow(() -> {
                    detailNotFoundCounter.increment();
                    return new NotFoundException("Member " + id + " not found");
                });
    }
}
