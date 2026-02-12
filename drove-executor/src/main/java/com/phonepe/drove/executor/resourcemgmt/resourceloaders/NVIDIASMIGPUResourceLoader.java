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
import java.util.stream.Collectors;
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
        val gpuList = IntStream.range(0, gpuCount).mapToObj((i) -> {
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
//        String coreCountCmd = "cat /Users/shubham.sharma/workspace/phonepe-drove/h100.xml";
        return String.join("", parseCommandOutput(coreCountCmd, false));
    }

    @SneakyThrows
    @Override
    public Set<GPUInfo> loadGPUInfo() {
        return fetchGpuResourcesWithNvidiaSMI()
                .stream().map(tup -> {
                    val gpuId = tup._1;
                    val gpuModel = tup._2;
                    val gpuBrand = tup._3;
                    log.info("Detected GPU - ID: {}, Brand: {} Model: {}", gpuId, gpuBrand, gpuModel);
                    return new GPUInfo(gpuId, gpuBrand, gpuModel, true);
                }).collect(Collectors.toSet());
    }
}
