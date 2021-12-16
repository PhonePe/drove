package com.phonepe.drove.executor.statemachine.actions;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.PullResponseItem;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
@Slf4j
public class ImagePullProgressHandler extends ResultCallback.Adapter<PullResponseItem> {

    private final Map<String, Long> downloadState = new ConcurrentHashMap<>();
    private final String image;

    public ImagePullProgressHandler(String image) {
        this.image = image;
    }

    @Override
    public void onNext(PullResponseItem responseItem) {
        val layerId = responseItem.getId();
        if (!Strings.isNullOrEmpty(layerId)) {
            val progressDetail = responseItem.getProgressDetail();
            if (null == progressDetail) {
                log.info("Image:{} Layer {}: {}", image, layerId, responseItem.getStatus());
            }
            else {
                val downloadPercent = (long) (((double) Objects.requireNonNullElse(progressDetail.getCurrent(), 0L)
                        / Objects.requireNonNullElse(progressDetail.getTotal(),
                                                     1L)) * 100);
                val oldValue = downloadState.put(layerId, downloadPercent);
                if (oldValue == null || !oldValue.equals(downloadPercent)) {
                    log.info("Image: {} Layer {}: {}%", image, layerId, downloadPercent);
                }

            }
        }
    }
}
