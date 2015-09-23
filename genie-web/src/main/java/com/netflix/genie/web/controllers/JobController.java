/*
 *
 *  Copyright 2015 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.genie.web.controllers;

import com.netflix.genie.common.dto.JobStatus;
import com.netflix.genie.common.exceptions.GenieException;
import com.netflix.genie.common.model.Job;
import com.netflix.genie.core.services.ExecutionService;
import com.netflix.genie.core.services.JobSearchService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import java.net.HttpURLConnection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * REST end-point for supporting jobs.
 *
 * @author amsharma
 * @author tgianos
 * @since 3.0.0
 */
@RestController
@RequestMapping(value = "/api/v3/jobs", produces = MediaType.APPLICATION_JSON_VALUE)
@Api(value = "jobs", tags = "jobs", description = "Manage Genie Jobs.")
public final class JobController {

    private static final Logger LOG = LoggerFactory.getLogger(JobController.class);
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final ExecutionService executionService;
    private final JobSearchService jobSearchService;

    /**
     * Constructor.
     *
     * @param executionService The execution service to use.
     * @param jobSearchService The job search service to use.
     */
    @Autowired
    public JobController(
            final ExecutionService executionService,
            final JobSearchService jobSearchService
    ) {
        this.executionService = executionService;
        this.jobSearchService = jobSearchService;
    }

    /**
     * Submit a new job.
     *
     * @param job                request object containing job info element for new job
     * @param clientHost         The header value for X-Forwarded-For
     * @param httpServletRequest The current request
     * @return The submitted job
     * @throws GenieException For any error
     */
    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(
            value = "Submit a job",
            notes = "Submit a new job to run to genie",
            response = Job.class
    )
    @ApiResponses(value = {
            @ApiResponse(
                    code = HttpURLConnection.HTTP_ACCEPTED,
                    message = "Accepted for processing",
                    response = Job.class
            ),
            @ApiResponse(
                    code = HttpURLConnection.HTTP_BAD_REQUEST,
                    message = "Bad Request"
            ),
            @ApiResponse(
                    code = HttpURLConnection.HTTP_CONFLICT,
                    message = "Job with ID already exists."
            ),
            @ApiResponse(
                    code = HttpURLConnection.HTTP_PRECON_FAILED,
                    message = "Precondition Failed"
            ),
            @ApiResponse(
                    code = HttpURLConnection.HTTP_INTERNAL_ERROR,
                    message = "Genie Server Error due to Unknown Exception"
            )
    })
    public ResponseEntity<Job> submitJob(
            @ApiParam(
                    value = "Job object to run.",
                    required = true
            )
            @RequestBody
            final Job job,
            @RequestHeader(FORWARDED_FOR_HEADER)
            final String clientHost,
            final HttpServletRequest httpServletRequest
    ) throws GenieException {
        if (job == null) {
            throw new GenieException(
                    HttpURLConnection.HTTP_PRECON_FAILED,
                    "No job entered. Unable to submit.");
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Called to submit job: " + job);
        }

        // get client's host from the context
        String localClientHost;
        if (StringUtils.isNotBlank(clientHost)) {
            localClientHost = clientHost.split(",")[0];
        } else {
            localClientHost = httpServletRequest.getRemoteAddr();
        }

        // set the clientHost, if it is not overridden already
        if (StringUtils.isNotBlank(localClientHost)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("called from: " + localClientHost);
            }
            job.setClientHost(localClientHost);
        }

        final Job createdJob = this.executionService.submitJob(job);
        final HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setLocation(
                ServletUriComponentsBuilder
                        .fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(createdJob.getId())
                        .toUri()
        );
        return new ResponseEntity<>(createdJob, httpHeaders, HttpStatus.ACCEPTED);
    }

    /**
     * Get job information for given job id.
     *
     * @param id id for job to look up
     * @return the Job
     * @throws GenieException For any error
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    @ApiOperation(
            value = "Find a job by id",
            notes = "Get the job by id if it exists",
            response = Job.class
    )
    @ApiResponses(value = {
            @ApiResponse(
                    code = HttpURLConnection.HTTP_BAD_REQUEST,
                    message = "Bad Request"
            ),
            @ApiResponse(
                    code = HttpURLConnection.HTTP_NOT_FOUND,
                    message = "Job not found"
            ),
            @ApiResponse(
                    code = HttpURLConnection.HTTP_PRECON_FAILED,
                    message = "Invalid id supplied"
            ),
            @ApiResponse(
                    code = HttpURLConnection.HTTP_INTERNAL_ERROR,
                    message = "Genie Server Error due to Unknown Exception"
            )
    })
    public Job getJob(
            @ApiParam(
                    value = "Id of the job to get.",
                    required = true
            )
            @PathVariable("id")
            final String id
    ) throws GenieException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("called for job with id: " + id);
        }
        return this.jobSearchService.getJob(id);
    }

//    /**
//     * Get job status for give job id.
//     *
//     * @param id id for job to look up
//     * @return The status of the job
//     * @throws GenieException For any error
//     */
//    @RequestMapping(value = "/{id}/status", method = RequestMethod.GET)
//    @ApiOperation(
//            value = "Get the status of the job ",
//            notes = "Get the status of job whose id is sent",
//            response = String.class
//    )
//    @ApiResponses(value = {
//            @ApiResponse(
//                    code = HttpURLConnection.HTTP_BAD_REQUEST,
//                    message = "Bad Request"
//            ),
//            @ApiResponse(
//                    code = HttpURLConnection.HTTP_NOT_FOUND,
//                    message = "Job not found"
//            ),
//            @ApiResponse(
//                    code = HttpURLConnection.HTTP_PRECON_FAILED,
//                    message = "Invalid id supplied"
//            ),
//            @ApiResponse(
//                    code = HttpURLConnection.HTTP_INTERNAL_ERROR,
//                    message = "Genie Server Error due to Unknown Exception"
//            )
//    })
//    public ObjectNode getJobStatus(
//            @ApiParam(
//                    value = "Id of the job.",
//                    required = true
//            )
//            @PathVariable("id")
//            final String id
//    ) throws GenieException {
//        if (LOG.isDebugEnabled()) {
//            LOG.debug("Called for job id:" + id);
//        }
//        final ObjectMapper mapper = new ObjectMapper();
//        final ObjectNode node = mapper.createObjectNode();
//        node.put("status", this.jobService.getJobStatus(id).toString());
//        return node;
//    }

    /**
     * Get jobs for given filter criteria.
     *
     * @param id          id for job
     * @param name        name of job (can be a SQL-style pattern such as HIVE%)
     * @param userName    user who submitted job
     * @param statuses    statuses of jobs to find
     * @param tags        tags for the job
     * @param clusterName the name of the cluster
     * @param clusterId   the id of the cluster
     * @param commandName the name of the command run by the job
     * @param commandId   the id of the command run by the job
     * @param page        page number for job
     * @param limit       max number of jobs to return
     * @param descending  Whether the order of the results should be descending or ascending
     * @param orderBys    Fields to order the results by
     * @return successful response, or one with HTTP error code
     * @throws GenieException For any error
     */
    @RequestMapping(method = RequestMethod.GET)
    @ApiOperation(
            value = "Find jobs",
            notes = "Find jobs by the submitted criteria.",
            response = Job.class,
            responseContainer = "List"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    code = HttpURLConnection.HTTP_BAD_REQUEST,
                    message = "Bad Request"
            ),
            @ApiResponse(
                    code = HttpURLConnection.HTTP_NOT_FOUND,
                    message = "Job not found"
            ),
            @ApiResponse(
                    code = HttpURLConnection.HTTP_PRECON_FAILED,
                    message = "Invalid id supplied"
            ),
            @ApiResponse(
                    code = HttpURLConnection.HTTP_INTERNAL_ERROR,
                    message = "Genie Server Error due to Unknown Exception"
            )
    })
    public List<Job> getJobs(
            @ApiParam(
                    value = "Id of the job."
            )
            @RequestParam(value = "id", required = false)
            final String id,
            @ApiParam(
                    value = "Name of the job."
            )
            @RequestParam(value = "name", required = false)
            final String name,
            @ApiParam(
                    value = "Name of the user who submitted the job."
            )
            @RequestParam(value = "userName", required = false)
            final String userName,
            @ApiParam(
                    value = "Statuses of the jobs to fetch.",
                    allowableValues = "INIT, RUNNING, SUCCEEDED, KILLED, FAILED"
            )
            @RequestParam(value = "status", required = false)
            final Set<String> statuses,
            @ApiParam(
                    value = "Tags for the job."
            )
            @RequestParam(value = "tag", required = false)
            final Set<String> tags,
            @ApiParam(
                    value = "Name of the cluster on which the job ran."
            )
            @RequestParam(value = "executionClusterName", required = false)
            final String clusterName,
            @ApiParam(
                    value = "Id of the cluster on which the job ran."
            )
            @RequestParam(value = "executionClusterId", required = false)
            final String clusterId,
            @ApiParam(
                    value = "The page to start on."
            )
            @RequestParam(value = "commandName", required = false)
            final String commandName,
            @ApiParam(
                    value = "Id of the cluster on which the job ran."
            )
            @RequestParam(value = "commandId", required = false)
            final String commandId,
            @ApiParam(
                    value = "The page to start on."
            )
            @RequestParam(value = "page", defaultValue = "0")
            final int page,
            @ApiParam(
                    value = "Max number of results per page."
            )
            @RequestParam(value = "limit", defaultValue = "1024")
            final int limit,
            @ApiParam(
                    value = "Whether results should be sorted in descending or ascending order. Defaults to descending"
            )
            @RequestParam(value = "descending", defaultValue = "true")
            final boolean descending,
            @ApiParam(
                    value = "The fields to order the results by. Must not be collection fields. Default is updated."
            )
            @RequestParam(value = "orderBy", required = false)
            final Set<String> orderBys
    ) throws GenieException {
        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "Called with [id | jobName | userName | statuses | executionClusterName "
                            + "| executionClusterId | page | limit | descending | orderBys]"
            );
            LOG.debug(id
                            + " | "
                            + name
                            + " | "
                            + userName
                            + " | "
                            + statuses
                            + " | "
                            + tags
                            + " | "
                            + clusterName
                            + " | "
                            + clusterId
                            + " | "
                            + commandName
                            + " | "
                            + commandId
                            + " | "
                            + page
                            + " | "
                            + limit
                            + " | "
                            + descending
                            + " | "
                            + orderBys
            );
        }
        Set<JobStatus> enumStatuses = null;
        if (statuses != null && !statuses.isEmpty()) {
            enumStatuses = EnumSet.noneOf(JobStatus.class);
            for (final String status : statuses) {
                if (StringUtils.isNotBlank(status)) {
                    enumStatuses.add(JobStatus.parse(status));
                }
            }
        }

        return this.jobSearchService.getJobs(
                id,
                name,
                userName,
                enumStatuses,
                tags,
                clusterName,
                clusterId,
                commandName,
                commandId,
                page,
                limit,
                descending,
                orderBys
        );
    }

    /**
     * Kill job based on given job ID.
     *
     * @param id id for job to kill
     * @return The job that was killed
     * @throws GenieException For any error
     */
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    @ApiOperation(
            value = "Delete a job",
            notes = "Delete the job with the id specified.",
            response = Job.class
    )
    @ApiResponses(value = {
            @ApiResponse(
                    code = HttpURLConnection.HTTP_NOT_FOUND,
                    message = "Job not found"
            ),
            @ApiResponse(
                    code = HttpURLConnection.HTTP_PRECON_FAILED,
                    message = "Invalid id supplied"
            ),
            @ApiResponse(
                    code = HttpURLConnection.HTTP_INTERNAL_ERROR,
                    message = "Genie Server Error due to Unknown Exception"
            )
    })
    public Job killJob(
            @ApiParam(
                    value = "Id of the job.",
                    required = true
            )
            @PathVariable("id")
            final String id
    ) throws GenieException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Called for job id: " + id);
        }
        return this.executionService.killJob(id);
    }
}
