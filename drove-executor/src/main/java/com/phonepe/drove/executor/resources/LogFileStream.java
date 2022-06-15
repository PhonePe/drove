package com.phonepe.drove.executor.resources;

import com.codahale.metrics.annotation.Metered;
import com.google.common.base.Strings;
import com.phonepe.drove.auth.model.DroveUserRole;
import com.phonepe.drove.common.coverageutils.IgnoreInJacocoGeneratedReport;
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

/**
 *
 */
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
@Path("/v1/logs/filestream")
@RolesAllowed(DroveUserRole.Values.DROVE_CLUSTER_NODE_ROLE)
@IgnoreInJacocoGeneratedReport
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
    private static class LogBuffer {
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
        try (val list = Files.list(new File(logPath).toPath())) {
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
        catch (IOException e) {
            return error("Could not read logs from " + logPath + ": " + e.getMessage());
        }
    }

    @GET
    @Path("/{appId}/{instanceId}/read/{fileName}")
    @Metered
    public Response streamLogs(
            @PathParam("appId") @NotEmpty final String appId,
            @PathParam("instanceId") @NotEmpty final String instanceId,
            @PathParam("fileName") @NotEmpty final String fileName,
            @QueryParam("offset") @Min(-1) @DefaultValue("-1") final long offset,
            @QueryParam("length") @Min(-1) @Max(Long.MAX_VALUE) @DefaultValue("-1") final int length) {
        val logFilePath = logInfo.logPathFor(appId, instanceId);
        if (Strings.isNullOrEmpty(logFilePath)) {
            return error("This only works if the 'drove' appender type is configured");
        }
        val extractedFileName = FilenameUtils.getName(fileName);
        val fullPath = logFilePath + "/" + extractedFileName;
        val logFile = new File(fullPath);
        log.trace("File read request: file={} offset={} length={} full-path={}",
                  extractedFileName, offset, length, fullPath);
        if (!logFile.exists() || !logFile.isFile() || !logFile.canRead()) {
            return error("Could not read log file: " + logFilePath);
        }
        if(offset == -1 && length==-1) {
            return Response.ok(new LogBuffer("", logFile.length())).build();
        }
        val actualLength = length == -1 ? 32_768 : length;
        try(val rf = new RandomAccessFile(logFile, "r")) {
            rf.seek(offset);
            var buf = new byte[actualLength];
            val bytesRead = rf.read(buf);
            return Response.ok(
                    new LogBuffer(bytesRead > 0 ?
                                  new String(buf, 0, bytesRead)
                                  : "",
                                  offset)).build();
        }
        catch (IOException e) {
            return error("Error reading file " + logFilePath + ": " + e.getMessage());
        }
    }

    @GET
    @Path("/{appId}/{instanceId}/download/{fileName}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Metered
    public Response downloadLogFile(
            @PathParam("appId") @NotEmpty final String appId,
            @PathParam("instanceId") @NotEmpty final String instanceId,
            @PathParam("fileName") @NotEmpty final String fileName) {
        val logFilePath = logInfo.logPathFor(appId, instanceId);
        if (Strings.isNullOrEmpty(logFilePath)) {
            return error("This only works if the 'drove' appender type is configured");
        }
        val extractedFileName = FilenameUtils.getName(fileName);
        val logFile = new File(logFilePath + "/" + extractedFileName);
        log.debug("File read request: file={} path={}", extractedFileName, logFilePath);
        if (!logFile.exists() || !logFile.isFile() || !logFile.canRead()) {
            return error("Could not read log file: " + logFilePath);
        }
        val so = new StreamingOutput() {
            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                try (val br = new BufferedReader(new FileReader(logFile), DEFAULT_BUFFER_SIZE);
                     val bw = new BufferedWriter(new OutputStreamWriter(output), DEFAULT_BUFFER_SIZE)) {
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
    }

    private Response error(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of(ERROR_FIELD, message))
                .build();
    }

}
