/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.mapreduce.v2.hs.webapp;

import static org.apache.hadoop.yarn.webapp.WebServicesTestUtils.assertResponseStatusCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.http.JettyUtils;
import org.apache.hadoop.mapreduce.v2.api.records.JobId;
import org.apache.hadoop.mapreduce.v2.api.records.JobState;
import org.apache.hadoop.mapreduce.v2.app.AppContext;
import org.apache.hadoop.mapreduce.v2.app.job.Job;
import org.apache.hadoop.mapreduce.v2.hs.HistoryContext;
import org.apache.hadoop.mapreduce.v2.hs.MockHistoryContext;
import org.apache.hadoop.mapreduce.v2.util.MRApps;
import org.apache.hadoop.yarn.api.ApplicationClientProtocol;
import org.apache.hadoop.yarn.webapp.GenericExceptionHandler;
import org.apache.hadoop.yarn.webapp.JerseyTestBase;
import org.apache.hadoop.yarn.webapp.WebApp;
import org.apache.hadoop.yarn.webapp.WebServicesTestUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.Test;

import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.jettison.JettisonFeature;
import org.glassfish.jersey.server.ResourceConfig;
/**
 * Test the history server Rest API for getting jobs with various query
 * parameters.
 *
 * /ws/v1/history/mapreduce/jobs?{query=value}
 */
public class TestHsWebServicesJobsQuery extends JerseyTestBase {

  private static Configuration conf = new Configuration();
  private static MockHistoryContext appContext;
  private static HsWebApp webApp;
  private static ApplicationClientProtocol acp = mock(ApplicationClientProtocol.class);

  @Override
  protected Application configure() {
    ResourceConfig config = new ResourceConfig();
    config.register(new JerseyBinder());
    config.register(HsWebServices.class);
    config.register(GenericExceptionHandler.class);
    config.register(new JettisonFeature()).register(JAXBContextResolver.class);
    return config;
  }

  private class JerseyBinder extends AbstractBinder {
    @Override
    protected void configure() {
      appContext = new MockHistoryContext(3, 2, 1);
      webApp = mock(HsWebApp.class);
      when(webApp.name()).thenReturn("hsmockwebapp");

      bind(webApp).to(WebApp.class).named("hsWebApp");
      bind(appContext).to(AppContext.class);
      bind(appContext).to(HistoryContext.class).named("ctx");
      bind(conf).to(Configuration.class).named("conf");
      bind(acp).to(ApplicationClientProtocol.class).named("appClient");
      final HttpServletResponse response = mock(HttpServletResponse.class);
      bind(response).to(HttpServletResponse.class);
      final HttpServletRequest request = mock(HttpServletRequest.class);
      bind(request).to(HttpServletRequest.class);
    }
  }

  @Test
  public void testJobsQueryStateNone() throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();

    ArrayList<JobState> jobStates = new ArrayList<>(Arrays.asList(JobState.values()));

    // find a state that isn't in use
    Map<JobId, Job> jobsMap = appContext.getAllJobs();
    for (Map.Entry<JobId, Job> entry : jobsMap.entrySet()) {
      jobStates.remove(entry.getValue().getState());
    }

    assertTrue(jobStates.size() > 0, "No unused job states");
    JobState notInUse = jobStates.get(0);

    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs").queryParam("state", notInUse.toString())
        .request(MediaType.APPLICATION_JSON).get(Response.class);

    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject json = response.readEntity(JSONObject.class);
    assertEquals(1, json.length(), "incorrect number of elements");
    assertEquals("", json.get("jobs").toString(), "jobs is not empty");
  }

  @Test
  public void testJobsQueryState() throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();
    // we only create 3 jobs and it cycles through states so we should have 3 unique states
    Map<JobId, Job> jobsMap = appContext.getAllJobs();
    String queryState = "BOGUS";
    JobId jid = null;
    for (Map.Entry<JobId, Job> entry : jobsMap.entrySet()) {
      jid = entry.getValue().getID();
      queryState = entry.getValue().getState().toString();
      break;
    }
    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs").queryParam("state", queryState)
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject json = response.readEntity(JSONObject.class);
    assertEquals(1, json.length(), "incorrect number of elements");
    JSONObject jsonJobs = json.getJSONObject("jobs");
    JSONObject jsonJob = jsonJobs.getJSONObject("job");
    JSONArray arr = new JSONArray();
    arr.put(jsonJob);
    assertEquals(1, arr.length(), "incorrect number of elements");
    JSONObject info = arr.getJSONObject(0);
    Job job = appContext.getPartialJob(jid);
    VerifyJobsUtils.verifyHsJobPartial(info, job);
  }

  @Test
  public void testJobsQueryStateInvalid() throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();

    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs").queryParam("state", "InvalidState")
        .request(MediaType.APPLICATION_JSON).get(Response.class);

    assertResponseStatusCode(Response.Status.BAD_REQUEST, response.getStatusInfo());
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject msg = response.readEntity(JSONObject.class);
    JSONObject exception = msg.getJSONObject("RemoteException");
    assertEquals(3, exception.length(), "incorrect number of elements");
    String message = exception.getString("message");
    String type = exception.getString("exception");
    String classname = exception.getString("javaClassName");
    WebServicesTestUtils
        .checkStringContains(
            "exception message",
            "org.apache.hadoop.mapreduce.v2.api.records.JobState.InvalidState",
            message);
    WebServicesTestUtils.checkStringMatch("exception type",
        "IllegalArgumentException", type);
    WebServicesTestUtils.checkStringMatch("exception classname",
        "java.lang.IllegalArgumentException", classname);
  }


  @Test
  public void testJobsQueryUserNone() throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();
    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs").queryParam("user", "bogus")
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject json = response.readEntity(JSONObject.class);
    assertEquals(1, json.length(), "incorrect number of elements");
    assertEquals("", json.get("jobs").toString(), "jobs is not empty");
  }

  @Test
  public void testJobsQueryUser() throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();
    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs").queryParam("user", "mock")
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject json = response.readEntity(JSONObject.class);

    assertEquals(1, json.length(), "incorrect number of elements");
    JSONObject jobs = json.getJSONObject("jobs");
    JSONArray arr = jobs.getJSONArray("job");
    assertEquals(3, arr.length(), "incorrect number of elements");
    // just verify one of them.
    JSONObject info = arr.getJSONObject(0);
    Job job = appContext.getPartialJob(MRApps.toJobID(info.getString("id")));
    VerifyJobsUtils.verifyHsJobPartial(info, job);
  }

  @Test
  public void testJobsQueryLimit() throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();
    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs").queryParam("limit", "2")
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject json = response.readEntity(JSONObject.class);
    assertEquals(1, json.length(), "incorrect number of elements");
    JSONObject jobs = json.getJSONObject("jobs");
    JSONArray arr = jobs.getJSONArray("job");
    // make sure we get 2 back
    assertEquals(2, arr.length(), "incorrect number of elements");
  }

  @Test
  public void testJobsQueryLimitInvalid() throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();

    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs").queryParam("limit", "-1")
        .request(MediaType.APPLICATION_JSON).get(Response.class);

    assertResponseStatusCode(Response.Status.BAD_REQUEST, response.getStatusInfo());
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject msg = response.readEntity(JSONObject.class);
    JSONObject exception = msg.getJSONObject("RemoteException");
    assertEquals(3, exception.length(), "incorrect number of elements");
    String message = exception.getString("message");
    String type = exception.getString("exception");
    String classname = exception.getString("javaClassName");
    WebServicesTestUtils.checkStringMatch("exception message",
        "limit value must be greater then 0", message);
    WebServicesTestUtils.checkStringMatch("exception type",
        "BadRequestException", type);
    WebServicesTestUtils.checkStringMatch("exception classname",
        "org.apache.hadoop.yarn.webapp.BadRequestException", classname);
  }

  @Test
  public void testJobsQueryQueue() throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();
    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs").queryParam("queue", "mockqueue")
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject json = response.readEntity(JSONObject.class);
    assertEquals(1, json.length(), "incorrect number of elements");
    JSONObject jobs = json.getJSONObject("jobs");
    JSONArray arr = jobs.getJSONArray("job");
    assertEquals(3, arr.length(), "incorrect number of elements");
  }

  @Test
  public void testJobsQueryQueueNonExist() throws Exception {
    WebTarget r = targetWithJsonObject();
    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs").queryParam("queue", "bogus")
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject json = response.readEntity(JSONObject.class);
    assertEquals(1, json.length(), "incorrect number of elements");
    assertEquals("", json.get("jobs").toString(), "jobs is not empty");
  }

  @Test
  public void testJobsQueryStartTimeEnd() throws Exception {
    WebTarget r = targetWithJsonObject();
    // the mockJobs start time is the current time - some random amount
    Long now = System.currentTimeMillis();
    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs")
        .queryParam("startedTimeEnd", String.valueOf(now))
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject json = response.readEntity(JSONObject.class);
    assertEquals(1, json.length(), "incorrect number of elements");
    JSONObject jobs = json.getJSONObject("jobs");
    JSONArray arr = jobs.getJSONArray("job");
    assertEquals(3, arr.length(), "incorrect number of elements");
  }

  @Test
  public void testJobsQueryStartTimeBegin() throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();
    // the mockJobs start time is the current time - some random amount
    Long now = System.currentTimeMillis();
    Response response =  r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs")
        .queryParam("startedTimeBegin", String.valueOf(now))
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject json = response.readEntity(JSONObject.class);
    assertEquals(1, json.length(), "incorrect number of elements");
    assertEquals("", json.get("jobs").toString(), "jobs is not empty");
  }

  @Test
  public void testJobsQueryStartTimeBeginEnd() throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();
    Map<JobId, Job> jobsMap = appContext.getAllJobs();
    int size = jobsMap.size();
    ArrayList<Long> startTime = new ArrayList<>(size);
    // figure out the middle start Time
    for (Map.Entry<JobId, Job> entry : jobsMap.entrySet()) {
      startTime.add(entry.getValue().getReport().getStartTime());
    }
    Collections.sort(startTime);

    assertTrue(size >= 3, "Error we must have atleast 3 jobs");
    long midStartTime = startTime.get(size - 2);

    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs")
        .queryParam("startedTimeBegin", String.valueOf(40000))
        .queryParam("startedTimeEnd", String.valueOf(midStartTime))
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject json = response.readEntity(JSONObject.class);
    assertEquals(1, json.length(), "incorrect number of elements");
    JSONObject jobs = json.getJSONObject("jobs");
    JSONArray arr = jobs.getJSONArray("job");
    assertEquals(size - 1, arr.length(), "incorrect number of elements");
  }

  @Test
  public void testJobsQueryStartTimeBeginEndInvalid() throws JSONException,
      Exception {
    WebTarget r = targetWithJsonObject();
    Long now = System.currentTimeMillis();
    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs")
        .queryParam("startedTimeBegin", String.valueOf(now))
        .queryParam("startedTimeEnd", String.valueOf(40000))
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertResponseStatusCode(Response.Status.BAD_REQUEST, response.getStatusInfo());
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject msg = response.readEntity(JSONObject.class);
    JSONObject exception = msg.getJSONObject("RemoteException");
    assertEquals(3, exception.length(), "incorrect number of elements");
    String message = exception.getString("message");
    String type = exception.getString("exception");
    String classname = exception.getString("javaClassName");
    WebServicesTestUtils
        .checkStringMatch(
            "exception message",
            "startedTimeEnd must be greater than startTimeBegin",
            message);
    WebServicesTestUtils.checkStringMatch("exception type",
        "BadRequestException", type);
    WebServicesTestUtils.checkStringMatch("exception classname",
        "org.apache.hadoop.yarn.webapp.BadRequestException", classname);
  }

  @Test
  public void testJobsQueryStartTimeInvalidformat() throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();
    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs").queryParam("startedTimeBegin", "efsd")
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertResponseStatusCode(Response.Status.BAD_REQUEST, response.getStatusInfo());
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject msg = response.readEntity(JSONObject.class);
    JSONObject exception = msg.getJSONObject("RemoteException");
    assertEquals(3, exception.length(), "incorrect number of elements");
    String message = exception.getString("message");
    String type = exception.getString("exception");
    String classname = exception.getString("javaClassName");
    WebServicesTestUtils
        .checkStringMatch(
            "exception message",
            "Invalid number format: For input string: \"efsd\"",
            message);
    WebServicesTestUtils.checkStringMatch("exception type",
        "BadRequestException", type);
    WebServicesTestUtils.checkStringMatch("exception classname",
        "org.apache.hadoop.yarn.webapp.BadRequestException", classname);
  }

  @Test
  public void testJobsQueryStartTimeEndInvalidformat() throws JSONException,
      Exception {
    WebTarget r = targetWithJsonObject();
    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs").queryParam("startedTimeEnd", "efsd")
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertResponseStatusCode(Response.Status.BAD_REQUEST, response.getStatusInfo());
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject msg = response.readEntity(JSONObject.class);
    JSONObject exception = msg.getJSONObject("RemoteException");
    assertEquals(3, exception.length(), "incorrect number of elements");
    String message = exception.getString("message");
    String type = exception.getString("exception");
    String classname = exception.getString("javaClassName");
    WebServicesTestUtils
        .checkStringMatch(
            "exception message",
            "Invalid number format: For input string: \"efsd\"",
            message);
    WebServicesTestUtils.checkStringMatch("exception type",
        "BadRequestException", type);
    WebServicesTestUtils.checkStringMatch("exception classname",
        "org.apache.hadoop.yarn.webapp.BadRequestException", classname);
  }

  @Test
  public void testJobsQueryStartTimeNegative() throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();
    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs")
        .queryParam("startedTimeBegin", String.valueOf(-1000))
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertResponseStatusCode(Response.Status.BAD_REQUEST, response.getStatusInfo());
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject msg = response.readEntity(JSONObject.class);
    JSONObject exception = msg.getJSONObject("RemoteException");
    assertEquals(3, exception.length(), "incorrect number of elements");
    String message = exception.getString("message");
    String type = exception.getString("exception");
    String classname = exception.getString("javaClassName");
    WebServicesTestUtils
        .checkStringMatch("exception message",
            "startedTimeBegin must be greater than 0",
            message);
    WebServicesTestUtils.checkStringMatch("exception type",
        "BadRequestException", type);
    WebServicesTestUtils.checkStringMatch("exception classname",
        "org.apache.hadoop.yarn.webapp.BadRequestException", classname);
  }

  @Test
  public void testJobsQueryStartTimeEndNegative() throws JSONException,
      Exception {
    WebTarget r = targetWithJsonObject();
    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs")
        .queryParam("startedTimeEnd", String.valueOf(-1000))
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertResponseStatusCode(Response.Status.BAD_REQUEST, response.getStatusInfo());
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject msg = response.readEntity(JSONObject.class);
    JSONObject exception = msg.getJSONObject("RemoteException");
    assertEquals(3, exception.length(), "incorrect number of elements");
    String message = exception.getString("message");
    String type = exception.getString("exception");
    String classname = exception.getString("javaClassName");
    WebServicesTestUtils.checkStringMatch("exception message",
        "startedTimeEnd must be greater than 0", message);
    WebServicesTestUtils.checkStringMatch("exception type",
        "BadRequestException", type);
    WebServicesTestUtils.checkStringMatch("exception classname",
        "org.apache.hadoop.yarn.webapp.BadRequestException", classname);
  }

  @Test
  public void testJobsQueryFinishTimeEndNegative() throws JSONException,
      Exception {
    WebTarget r = targetWithJsonObject();
    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs")
        .queryParam("finishedTimeEnd", String.valueOf(-1000))
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertResponseStatusCode(Response.Status.BAD_REQUEST, response.getStatusInfo());
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject msg = response.readEntity(JSONObject.class);
    JSONObject exception = msg.getJSONObject("RemoteException");
    assertEquals(3, exception.length(), "incorrect number of elements");
    String message = exception.getString("message");
    String type = exception.getString("exception");
    String classname = exception.getString("javaClassName");
    WebServicesTestUtils.checkStringMatch("exception message",
        "finishedTimeEnd must be greater than 0", message);
    WebServicesTestUtils.checkStringMatch("exception type",
        "BadRequestException", type);
    WebServicesTestUtils.checkStringMatch("exception classname",
        "org.apache.hadoop.yarn.webapp.BadRequestException", classname);
  }

  @Test
  public void testJobsQueryFinishTimeBeginNegative() throws JSONException,
      Exception {
    WebTarget r = targetWithJsonObject();
    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs")
        .queryParam("finishedTimeBegin", String.valueOf(-1000))
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertResponseStatusCode(Response.Status.BAD_REQUEST, response.getStatusInfo());
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject msg = response.readEntity(JSONObject.class);
    JSONObject exception = msg.getJSONObject("RemoteException");
    assertEquals(3, exception.length(), "incorrect number of elements");
    String message = exception.getString("message");
    String type = exception.getString("exception");
    String classname = exception.getString("javaClassName");
    WebServicesTestUtils.checkStringMatch("exception message",
        "finishedTimeBegin must be greater than 0", message);
    WebServicesTestUtils.checkStringMatch("exception type",
        "BadRequestException", type);
    WebServicesTestUtils.checkStringMatch("exception classname",
        "org.apache.hadoop.yarn.webapp.BadRequestException", classname);
  }

  @Test
  public void testJobsQueryFinishTimeBeginEndInvalid() throws JSONException,
      Exception {
    WebTarget r = targetWithJsonObject();
    Long now = System.currentTimeMillis();
    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs")
        .queryParam("finishedTimeBegin", String.valueOf(now))
        .queryParam("finishedTimeEnd", String.valueOf(40000))
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertResponseStatusCode(Response.Status.BAD_REQUEST, response.getStatusInfo());
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject msg = response.readEntity(JSONObject.class);
    JSONObject exception = msg.getJSONObject("RemoteException");
    assertEquals(3, exception.length(), "incorrect number of elements");
    String message = exception.getString("message");
    String type = exception.getString("exception");
    String classname = exception.getString("javaClassName");
    WebServicesTestUtils
        .checkStringMatch(
            "exception message",
            "finishedTimeEnd must be greater than finishedTimeBegin",
            message);
    WebServicesTestUtils.checkStringMatch("exception type",
        "BadRequestException", type);
    WebServicesTestUtils.checkStringMatch("exception classname",
        "org.apache.hadoop.yarn.webapp.BadRequestException", classname);
  }

  @Test
  public void testJobsQueryFinishTimeInvalidformat() throws JSONException,
      Exception {
    WebTarget r = targetWithJsonObject();
    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs").queryParam("finishedTimeBegin", "efsd")
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertResponseStatusCode(Response.Status.BAD_REQUEST, response.getStatusInfo());
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject msg = response.readEntity(JSONObject.class);
    JSONObject exception = msg.getJSONObject("RemoteException");
    assertEquals(3, exception.length(), "incorrect number of elements");
    String message = exception.getString("message");
    String type = exception.getString("exception");
    String classname = exception.getString("javaClassName");
    WebServicesTestUtils
        .checkStringMatch(
            "exception message",
            "Invalid number format: For input string: \"efsd\"",
            message);
    WebServicesTestUtils.checkStringMatch("exception type",
        "BadRequestException", type);
    WebServicesTestUtils.checkStringMatch("exception classname",
        "org.apache.hadoop.yarn.webapp.BadRequestException", classname);
  }

  @Test
  public void testJobsQueryFinishTimeEndInvalidformat() throws JSONException,
      Exception {
    WebTarget r = targetWithJsonObject();
    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs").queryParam("finishedTimeEnd", "efsd")
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertResponseStatusCode(Response.Status.BAD_REQUEST, response.getStatusInfo());
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject msg = response.readEntity(JSONObject.class);
    JSONObject exception = msg.getJSONObject("RemoteException");
    assertEquals(3, exception.length(), "incorrect number of elements");
    String message = exception.getString("message");
    String type = exception.getString("exception");
    String classname = exception.getString("javaClassName");
    WebServicesTestUtils
        .checkStringMatch(
            "exception message",
            "Invalid number format: For input string: \"efsd\"",
            message);
    WebServicesTestUtils.checkStringMatch("exception type",
        "BadRequestException", type);
    WebServicesTestUtils.checkStringMatch("exception classname",
        "org.apache.hadoop.yarn.webapp.BadRequestException", classname);
  }

  @Test
  public void testJobsQueryFinishTimeBegin() throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();
    // the mockJobs finish time is the current time + some random amount
    Long now = System.currentTimeMillis();
    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs")
        .queryParam("finishedTimeBegin", String.valueOf(now))
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject json = response.readEntity(JSONObject.class);
    assertEquals(1, json.length(), "incorrect number of elements");
    JSONObject jobs = json.getJSONObject("jobs");
    JSONArray arr = jobs.getJSONArray("job");
    assertEquals(3, arr.length(), "incorrect number of elements");
  }

  @Test
  public void testJobsQueryFinishTimeEnd() throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();
    // the mockJobs finish time is the current time + some random amount
    Long now = System.currentTimeMillis();
    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs")
        .queryParam("finishedTimeEnd", String.valueOf(now))
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject json = response.readEntity(JSONObject.class);
    assertEquals(1, json.length(), "incorrect number of elements");
    assertEquals("", json.get("jobs").toString(), "jobs is not empty");
  }

  @Test
  public void testJobsQueryFinishTimeBeginEnd() throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();

    Map<JobId, Job> jobsMap = appContext.getAllJobs();
    int size = jobsMap.size();
    // figure out the mid end time - we expect atleast 3 jobs
    ArrayList<Long> finishTime = new ArrayList<>(size);
    for (Map.Entry<JobId, Job> entry : jobsMap.entrySet()) {
      finishTime.add(entry.getValue().getReport().getFinishTime());
    }
    Collections.sort(finishTime);

    assertTrue(size >= 3, "Error we must have atleast 3 jobs");
    long midFinishTime = finishTime.get(size - 2);

    Response response = r.path("ws").path("v1").path("history")
        .path("mapreduce").path("jobs")
        .queryParam("finishedTimeBegin", String.valueOf(40000))
        .queryParam("finishedTimeEnd", String.valueOf(midFinishTime))
        .request(MediaType.APPLICATION_JSON).get(Response.class);
    assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
        response.getMediaType().toString());
    JSONObject json = response.readEntity(JSONObject.class);
    assertEquals(1, json.length(), "incorrect number of elements");
    JSONObject jobs = json.getJSONObject("jobs");
    JSONArray arr = jobs.getJSONArray("job");
    assertEquals(size - 1, arr.length(), "incorrect number of elements");
  }

}
