package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.google.common.base.Strings;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.models.application.requirements.GpuVendor;
import com.phonepe.drove.models.info.GPUInfo;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.stream.IntStream;

import static com.phonepe.drove.executor.resourcemgmt.resourceloaders.CommandUtils.parseCommandOutput;


@SuppressWarnings("java:S135")
@Slf4j
public class NVIDIASMIGPUResourceLoader implements GPUResourceLoader {
    private record GpuDescriptor(String gpuId, String productName, Integer gpuIndex, boolean migDevice) {}

    @SuppressWarnings({"java:S3776", "java:S112"})
    private List<GpuDescriptor> fetchGpuResourcesWithNvidiaSMI() throws Exception {
        var xmlOutput = "";
        try {
            xmlOutput = queryNvidiaSMI();
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("error=2")) {
                log.info("nvidia-smi command not found. This likely means that there is no NVIDIA GPU present in the system or the NVIDIA drivers are not installed. Returning empty GPU list.");
                return List.of();
            }
            throw e;
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setNamespaceAware(true);
        factory.setFeature("http://xml.org/sax/features/namespaces", false);
        factory.setFeature("http://xml.org/sax/features/validation", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);


        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xmlOutput)));
        int gpuCount = doc.getElementsByTagName("gpu").getLength();
        val gpuList = IntStream.range(0, gpuCount).boxed().flatMap(i -> {
            val gpuDoc = doc.getElementsByTagName("gpu").item(i);
            String gpuId = null;
            String productName = null;
            Integer gpuIndex = null;
            boolean migEnabled = false;
            val migDescriptors = new ArrayList<GpuDescriptor>();
            for (int j = 0; j < gpuDoc.getChildNodes().getLength(); j++) {
                val gpuPropDoc = gpuDoc.getChildNodes().item(j);
                if (gpuPropDoc.getNodeName().equalsIgnoreCase("product_name")) {
                    productName = gpuPropDoc.getTextContent();
                }
                if (gpuPropDoc.getNodeName().equalsIgnoreCase("uuid")
                        && !gpuPropDoc.getTextContent().isBlank()) {
                    // UUID is the stable GPU identity accepted by NVIDIA runtime and Docker device requests.
                    gpuId = gpuPropDoc.getTextContent().trim();
                }
                if (gpuPropDoc.getNodeName().equalsIgnoreCase("minor_number")) {
                    try {
                        gpuIndex = Integer.parseInt(gpuPropDoc.getTextContent().trim());
                    }
                    catch (Exception ignored) {
                        log.warn("Could not parse minor_number for GPU");
                    }
                }
                if (gpuPropDoc.getNodeName().equalsIgnoreCase("mig_mode")) {
                    migEnabled = isMigEnabled(gpuPropDoc);
                }
                if (gpuPropDoc.getNodeName().equalsIgnoreCase("mig_devices")) {
                    migDescriptors.addAll(parseMigDevices(gpuPropDoc, productName, gpuIndex));
                }
            }
            if(Strings.isNullOrEmpty(gpuId) || gpuIndex == null) {
                throw new RuntimeException("Unable to parse GPU information from nvidia-smi output. Missing required fields id: %s index: %d".formatted(gpuId, gpuIndex));
            }
            if (migEnabled && !migDescriptors.isEmpty()) {
                return migDescriptors.stream();
            }
            return List.of(new GpuDescriptor(gpuId, productName, gpuIndex, false)).stream();
        }).toList();
        log.info("Found {} GPU(s) in the system", gpuList.size());
        return gpuList;
    }

    private boolean isMigEnabled(Node migModeNode) {
        for (int i = 0; i < migModeNode.getChildNodes().getLength(); i++) {
            val modeProp = migModeNode.getChildNodes().item(i);
            if (modeProp.getNodeName().equalsIgnoreCase("current_mig")) {
                return "enabled".equalsIgnoreCase(modeProp.getTextContent().trim());
            }
        }
        return false;
    }

    private List<GpuDescriptor> parseMigDevices(Node migDevicesNode, String productName, Integer gpuIndex) {
        val descriptors = new ArrayList<GpuDescriptor>();
        for (int i = 0; i < migDevicesNode.getChildNodes().getLength(); i++) {
            val child = migDevicesNode.getChildNodes().item(i);
            if (!child.getNodeName().equalsIgnoreCase("mig_device")) {
                continue;
            }
            String migUuid = null;
            for (int j = 0; j < child.getChildNodes().getLength(); j++) {
                val migProp = child.getChildNodes().item(j);
                if (migProp.getNodeName().equalsIgnoreCase("uuid")) {
                    migUuid = migProp.getTextContent();
                    break;
                }
            }
            if (migUuid == null || migUuid.isBlank()) {
                continue;
            }
            descriptors.add(new GpuDescriptor(
                    migUuid.trim(),
                    "MIG " + Strings.nullToEmpty(productName).trim(),
                    gpuIndex,
                    true));
        }
        return descriptors;
    }

    @NotNull
    protected String queryNvidiaSMI() throws InterruptedException, IOException {
        String coreCountCmd = "nvidia-smi -q -x";
        return String.join("", parseCommandOutput(coreCountCmd, false));
    }

    @NotNull
    protected String queryNvidiaSMITopo() throws InterruptedException, IOException {
        return String.join("\n", parseCommandOutput("nvidia-smi topo -m", false));
    }

    @SuppressWarnings("java:S135")
    Map<Integer, Integer> parseNumaAffinityFromTopoOutput(final String topoOutput) {
        val result = new LinkedHashMap<Integer, Integer>();
        Integer numaAffinityColIndex = null;
        for (val line : topoOutput.split("\n")) {
            if (numaAffinityColIndex == null) {
                numaAffinityColIndex = findNumaAffinityColumnIndex(line).orElse(null);
                continue;
            }
            val trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("Legend")) {
                break;
            }
            val cols = splitTopoColumns(trimmed);
            if (cols.isEmpty() || !cols.get(0).matches("GPU\\d+")) {
                continue;
            }
            try {
                val gpuIndex = Integer.parseInt(cols.get(0).substring(3)); // strip "GPU" prefix
                val numaAffinity = Integer.parseInt(cols.get(numaAffinityColIndex));
                result.put(gpuIndex, numaAffinity);
            } catch (Exception e) {
                log.warn("Could not parse NUMA affinity from topo line: {}", line);
            }
        }
        return result;
    }

    private Optional<Integer> findNumaAffinityColumnIndex(String headerLine) {
        if (!headerLine.contains("NUMA Affinity")) {
            return Optional.empty();
        }
        // Both header and data rows use the same \s+ tokenization, so the index
        // of the "NUMA" token in the header equals the NUMA value index in data rows.
        val cols = splitTopoColumns(headerLine);
        return IntStream.range(0, cols.size())
                .filter(i -> cols.get(i).equalsIgnoreCase("NUMA"))
                .boxed()
                .findFirst();
    }

    private List<String> splitTopoColumns(String line) {
        return Arrays.stream(line.trim().split("\\s+"))
                .filter(col -> !col.isEmpty())
                .toList();
    }

    private Map<Integer, Integer> fetchNumaAffinityMap() throws InterruptedException {
        try {
            return parseNumaAffinityFromTopoOutput(queryNvidiaSMITopo());
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to fetch NUMA affinity from nvidia-smi topo: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @SneakyThrows
    @Override
    public Set<GPUInfo> loadGPUInfo() {
        val gpuList = fetchGpuResourcesWithNvidiaSMI();
        if (gpuList.isEmpty()) {
            return Collections.emptySet();
        }
        val numaAffinityMap = fetchNumaAffinityMap();
        val result = new HashSet<GPUInfo>();
        for (val gpuDescriptor : gpuList) {
            val gpuId = gpuDescriptor.gpuId();
            val gpuModel = gpuDescriptor.productName();
            val numaAffinity = Optional.ofNullable(gpuDescriptor.gpuIndex())
                    .map(numaAffinityMap::get)
                    .orElse(null);
            log.info("Detected {} GPU - ID: {}, Vendor: {}, Model: {}, NUMA Affinity: {}",
                     gpuDescriptor.migDevice() ? "MIG" : "physical", gpuId, GpuVendor.NVIDIA, gpuModel, numaAffinity);
            result.add(new GPUInfo(gpuId, GpuVendor.NVIDIA, CommonUtils.normalizeGPUModel(gpuModel), numaAffinity));
        }
        return result;
    }
}
