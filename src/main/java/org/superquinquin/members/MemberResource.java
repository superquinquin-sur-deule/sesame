package org.superquinquin.members;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

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
    private Counter photoUploadCounter;
    private Counter photoUploadRejectedCounter;

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
        photoUploadCounter = Counter.builder("sesame.members.photo_upload")
                .description("Number of member photo upload requests")
                .register(registry);
        photoUploadRejectedCounter = Counter.builder("sesame.members.photo_upload.rejected")
                .description("Number of member photo uploads rejected as invalid (400)")
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

    record PhotoUpload(@Schema(required = true, description = "Photo as a data URI or single-line base64") String photo) {}

    @POST
    @Path("/{id}/photo")
    @Consumes(MediaType.APPLICATION_JSON)
    public MemberDetail uploadPhoto(@PathParam("id") int id, PhotoUpload body) {
        Log.infof("Photo upload for member %d", id);
        photoUploadCounter.increment();
        final String base64;
        try {
            base64 = MemberRepository.extractBase64(body == null ? null : body.photo());
        } catch (IllegalArgumentException e) {
            photoUploadRejectedCounter.increment();
            throw new BadRequestException(e.getMessage());
        }
        if (repository.findById(id).isEmpty()) {
            throw new NotFoundException("Member " + id + " not found");
        }
        repository.updatePhoto(id, base64);
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Member " + id + " not found"));
    }
}
