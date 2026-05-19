package org.superquinquin.members;

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

    @GET
    public List<MemberSummary> search(@QueryParam("q") String q) {
        return repository.search(q);
    }

    @GET
    @Path("/{id}")
    public MemberDetail detail(@PathParam("id") int id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Member " + id + " not found"));
    }
}
