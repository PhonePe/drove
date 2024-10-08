/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.executor.resources;

import com.codahale.metrics.annotation.Metered;
import com.google.common.base.Strings;
import com.phonepe.drove.auth.model.DroveUserRole;
import com.phonepe.drove.executor.logging.LogInfo;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.FilenameUtils;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 *
 */
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Path("/v1/logs/filestream")
@RolesAllowed(DroveUserRole.Values.DROVE_CLUSTER_NODE_ROLE)
@SuppressWarnings("java:S1075")
public class LogFileStream {

    private static final String ERROR_FIELD = "error";
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    @Value
    public static class LogLines {
        long startOffset;
        long endOffset;
        List<String> lines;
    }

    @Value
    public static class LogBuffer {
        String data;
        long offset;
    }

    private final LogInfo logInfo;

    @Inject
    public LogFileStream(LogInfo logInfo) {
        this.logInfo = logInfo;
    }

    @GET
    @Path("/{appId}/{instanceId}/list")
    @Metered
    public Response listLogs(
            @PathParam("appId") @NotEmpty final String appId,
            @PathParam("instanceId") @NotEmpty final String instanceId) {
        val logPath = logInfo.logPathFor(appId, instanceId);
        if (Strings.isNullOrEmpty(logPath)) {
            return Response.ok(Map.of("files", List.of())).build();
        }
        try {
            val basePath = logInfo.getLogPrefix() + (logInfo.getLogPrefix().endsWith("/") ? "" : "/");
            val actualPath = new File(logPath).getCanonicalPath();
            if (!actualPath.startsWith(basePath)) {
                return error("Log list request to out of bounds path: " + actualPath);
            }
            try (val list = Files.list(java.nio.file.Path.of(actualPath))) {
                return Response.ok(
                                Map.of("files",
                                       list
                                               .filter(filePath -> FilenameUtils.getName(filePath.getFileName().toString())
                                                       .startsWith("output."))
                                               .map(java.nio.file.Path::toFile)
                                               .filter(logFile -> logFile.exists() && logFile.isFile() && logFile.canRead())
                                               .map(logFile -> FilenameUtils.getName(logFile.getAbsolutePath()))
                                               .toList()))
                        .build();
            }
        }
        catch (IOException e) {
            return error("Could not read logs from " + logPath + ": " + e.getMessage());
        }
    }

    @GET
    @Path("/{appId}/{instanceId}/read/{fileName}")
    @Metered
    @SuppressWarnings("javasecurity:S2083")
    public Response streamLogs(
            @PathParam("appId") @NotEmpty final String appId,
            @PathParam("instanceId") @NotEmpty final String instanceId,
            @PathParam("fileName") @NotEmpty final String fileName,
            @QueryParam("offset") @Min(-1) @DefaultValue("-1") final long offset,
            @QueryParam("length") @Min(-1) @Max(Long.MAX_VALUE) @DefaultValue("-1") final int length) {
        return handleLogFile(
                appId,
                instanceId,
                fileName,
                logFile -> {
                    if (offset == -1 && length == -1) {
                        return Response.ok(new LogBuffer("", logFile.length())).build();
                    }
                    val actualLength = length == -1 ? 32_768 : length;
                    try (val rf = new RandomAccessFile(logFile, "r")) {
                        rf.seek(offset);
                        var buf = new byte[actualLength];
                        val bytesRead = rf.read(buf);
                        return Response.ok(
                                        new LogBuffer(bytesRead > 0 ? new String(buf, 0, bytesRead) : "", offset))
                                .build();
                    }
                    catch (IOException e) {
                        return error("Error reading file " + logFile.toPath() + ": " + e.getMessage());
                    }
                });
    }

    @GET
    @Path("/{appId}/{instanceId}/download/{fileName}")
    @Produces({MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON})
    @Metered
    public Response downloadLogFile(
            @PathParam("appId") @NotEmpty final String appId,
            @PathParam("instanceId") @NotEmpty final String instanceId,
            @PathParam("fileName") @NotEmpty final String fileName) {
        return handleLogFile(appId,
                             instanceId,
                             fileName,
                             logFile -> {
                                 val so = new StreamingOutput() {
                                     @Override
                                     public void write(OutputStream output) throws IOException,
                                                                                   WebApplicationException {
                                         try (val br = new BufferedReader(new FileReader(logFile), DEFAULT_BUFFER_SIZE);
                                              val bw = new BufferedWriter(new OutputStreamWriter(output),
                                                                          DEFAULT_BUFFER_SIZE)) {
                                             var buffer = new char[DEFAULT_BUFFER_SIZE];
                                             int bytesRead;

                                             while ((bytesRead = br.read(buffer, 0, DEFAULT_BUFFER_SIZE)) != -1) {
                                                 bw.write(buffer, 0, bytesRead);
                                             }
                                             bw.flush();
                                         }
                                     }
                                 };
                                 return Response.ok(so).build();
                             });
    }

    private Response handleLogFile(
            String appId, String instanceId, String fileName,
            Function<File, Response> handler) {
        val logPath = logInfo.logPathFor(appId, instanceId);
        if (Strings.isNullOrEmpty(logPath)) {
            return error("This only works if the 'drove' appender type is configured");
        }
        val basePath = logInfo.getLogPrefix() + (logInfo.getLogPrefix().endsWith("/") ? "" : "/");
        try {
            val actualPath = new File(logPath).getCanonicalPath();
            if (!actualPath.startsWith(basePath)) {
                return error("Log read request to out of bounds directory: " + actualPath);
            }
            val extractedFileName = FilenameUtils.getName(fileName);
            val fullPath = logPath + "/" + extractedFileName;
            val logFile = new File(fullPath);
            val canonicalFilePath = logFile.getCanonicalPath();
            if (!canonicalFilePath.startsWith(basePath)) {
                return error("Log read request to out of bounds file: " + canonicalFilePath);
            }
            if (!logFile.canRead()) {
                return error("Could not read log file: " + fullPath);
            }
            return handler.apply(logFile);
        }
        catch (IOException e) {
            return error("Error reading file " + logPath + ": " + e.getMessage());
        }
    }

    private Response error(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of(ERROR_FIELD, message))
                .build();
    }

}
