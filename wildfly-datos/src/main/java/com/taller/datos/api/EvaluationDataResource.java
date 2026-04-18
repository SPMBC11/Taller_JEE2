package com.taller.datos.api;

import com.taller.datos.dto.PersistEvaluationRequest;
import com.taller.datos.dto.PersistEvaluationResponse;
import com.taller.datos.service.EvaluationDataService;
import jakarta.ejb.EJB;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/data/evaluations")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EvaluationDataResource {

    @EJB
    private EvaluationDataService evaluationDataService;

    @POST
    public Response persistEvaluation(PersistEvaluationRequest request) {
        try {
            PersistEvaluationResponse response = evaluationDataService.persistEvaluation(request);
            return Response.ok(response).build();
        } catch (IllegalArgumentException exception) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(exception.getMessage())
                    .build();
        }
    }
}
