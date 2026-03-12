package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.phonepe.drove.models.info.GPUInfo;
import io.vavr.Tuple3;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.stream.IntStream;

import static com.phonepe.drove.executor.resourcemgmt.resourceloaders.CommandUtils.parseCommandOutput;



@Slf4j
public class NVIDIASMIGPUResourceLoader implements GPUResourceLoader {


    private List<Tuple3<String, String, String>> fetchGpuResourcesWithNvidiaSMI() throws Exception {
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
        val gpuList = IntStream.range(0, gpuCount).mapToObj(i -> {
            val gpuDoc = doc.getElementsByTagName("gpu").item(i);
            val gpuId = gpuDoc.getAttributes().getNamedItem("id").getTextContent();
            var productName = "Unknown GPU";
            var productBrand = "Unknown Brand";
            for (int j = 0; j < gpuDoc.getChildNodes().getLength(); j++) {
                val gpuPropDoc = gpuDoc.getChildNodes().item(j);
                if (gpuPropDoc.getNodeName().equalsIgnoreCase("product_name")) {
                    productName = gpuPropDoc.getTextContent();
                }
                if (gpuPropDoc.getNodeName().equalsIgnoreCase("product_brand")) {
                    productBrand = gpuPropDoc.getTextContent();
                }
            }
            return new Tuple3<>(gpuId, productName, productBrand);
        }).toList();
        log.info("Found {} GPU(s) in the system", gpuList.size());
        return gpuList;
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
        boolean headerFound = false;
        for (val line : topoOutput.split("\n")) {
            if (!headerFound) {
                if (line.contains("NUMA Affinity")) {
                    headerFound = true;
                }
                continue;
            }
            val trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("Legend")) {
                break;
            }
            if (!trimmed.matches("GPU\\d+\\s+.*")) {
                continue;
            }
            val parts = trimmed.split("\\s+");
            try {
                val gpuIndex = Integer.parseInt(parts[0].substring(3)); // strip "GPU" prefix
                val numaStr = parts[parts.length - 1];
                val numaAffinity = Integer.parseInt(numaStr);
                result.put(gpuIndex, numaAffinity);
            } catch (NumberFormatException e) {
                log.warn("Could not parse NUMA affinity from topo line: {}", line);
            }
        }
        return result;
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
        for (int i = 0; i < gpuList.size(); i++) {
            val tup = gpuList.get(i);
            val gpuId = tup._1;
            val gpuModel = tup._2;
            val gpuBrand = tup._3;
            val numaAffinity = numaAffinityMap.get(i);
            log.info("Detected GPU - ID: {}, Brand: {}, Model: {}, NUMA Affinity: {}",
                     gpuId, gpuBrand, gpuModel, numaAffinity);
            result.add(new GPUInfo(gpuId, gpuBrand, gpuModel, numaAffinity, true));
        }
        return result;
    }
}
