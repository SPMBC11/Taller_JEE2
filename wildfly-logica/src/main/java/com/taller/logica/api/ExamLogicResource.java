package com.taller.logica.api;

import com.taller.logica.dto.FinishExamRequest;
import com.taller.logica.dto.FinishExamResponse;
import com.taller.logica.service.ExamLogicService;
import jakarta.ejb.EJB;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/logic/exams")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ExamLogicResource {

    @EJB
    private ExamLogicService examLogicService;

    @POST
    @Path("/finish")
    public Response finishExam(FinishExamRequest request) {
        try {
            FinishExamResponse response = examLogicService.finishExam(request);
            return Response.ok(response).build();
        } catch (IllegalArgumentException exception) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(exception.getMessage())
                    .build();
        }
    }
}
