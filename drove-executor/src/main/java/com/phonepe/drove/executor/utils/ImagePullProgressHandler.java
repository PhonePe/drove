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

package com.phonepe.drove.executor.utils;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.PullResponseItem;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
@Slf4j
public class ImagePullProgressHandler extends ResultCallback.Adapter<PullResponseItem> {

    private final Map<String, Long> downloadState = new ConcurrentHashMap<>();
    private final Map<String, String> mdc;
    private final String image;

    public ImagePullProgressHandler(Map<String, String> mdc, String image) {
        this.mdc = mdc;
        this.image = image;
    }

    @Override
    public void onNext(PullResponseItem responseItem) {
        if (null != mdc) {
            MDC.setContextMap(mdc);
        }
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
