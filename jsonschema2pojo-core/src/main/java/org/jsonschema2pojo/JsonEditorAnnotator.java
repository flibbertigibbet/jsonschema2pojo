/**
 * Copyright © 2010-2014 Nokia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jsonschema2pojo;

import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonArray;
import com.sun.codemodel.*;
import org.jsonschema2pojo.annotations.*;


public class JsonEditorAnnotator extends AbstractAnnotator {

    // as used by json-editor
    static int DEFAULT_PROPERTY_ORDER = 1000;

    static Logger Log = Logger.getLogger("JsonEditorAnnotator");

    @Override
    public void propertyOrder(JDefinedClass clazz, JsonNode propertiesNode) {
        JAnnotationArrayMember annotationValue = clazz.annotate(JsonPropertyOrder.class).paramArray("value");

        SortedMap<Integer, String> fieldOrders = new TreeMap();

        for (Iterator<String> properties = propertiesNode.fieldNames(); properties.hasNext();) {
            String propertyName = properties.next();

            if (propertyName.equals("_localId")) {
                continue;
            }

            JsonNode element = propertiesNode.findValue(propertyName);

            int order = DEFAULT_PROPERTY_ORDER;
            if (element != null && element.has("propertyOrder")) {
                JsonNode propertyOrder = element.findValue("propertyOrder");
                order = propertyOrder.asInt(DEFAULT_PROPERTY_ORDER);
            }

            fieldOrders.put(order, propertyName);
        }

        for (String name: fieldOrders.values()) {
            annotationValue.param(name);
        }
    }

    @Override
    public void propertyField(JFieldVar field, JDefinedClass clazz,
                              String propertyName, JsonNode propertyNode) {

        if (propertyNode.has("title")) {
            String title = propertyNode.get("title").asText();

            if (!title.isEmpty()) {
                JAnnotationUse titleAnnotation = field.annotate(Title.class);
                titleAnnotation.param("value", title);
            }
        }

        if (propertyNode.has("plural_title")) {
            String pluralTitle = propertyNode.get("plural_title").asText();

            if (!pluralTitle.isEmpty()) {
                JAnnotationUse pluralTitleAnnotation = field.annotate(PluralTitle.class);
                pluralTitleAnnotation.param("value", pluralTitle);
            }
        }

        if (propertyNode.has("multiple")) {
            Boolean multiple = propertyNode.get("multiple").asBoolean();
            JAnnotationUse multipleAnnotation = field.annotate(Multiple.class);
            multipleAnnotation.param("value", multiple);
        }

        if (propertyNode.has("fieldType")) {
            String fieldType = propertyNode.get("fieldType").asText();
            FieldTypes foundFieldType = FieldTypes.valueOf(fieldType);
            
            JAnnotationUse fieldTypeAnnotation = field.annotate(FieldType.class);
            fieldTypeAnnotation.param("value", foundFieldType);

            /* Additional annotations for reference types. To see how they're built in DRIVER:
             * https://github.com/WorldBank-Transport/DRIVER/blob/develop/schema_editor/app/scripts/schemas/schemas-service.js
             * NB: in DRIVER usage of json-editor, referenced field is always watch.target,
             * enumSource value is always _localId, and id is always the default 'item'.
             */
            if (foundFieldType == FieldTypes.reference) {

                // annotate with watch target (referenced field)
                JsonNode watch = propertyNode.get("watch");
                if (watch != null && watch.has("target")) {
                    String target = watch.get("target").asText();
                    if (target != null) {
                        JAnnotationUse watchTargetAnnotation = field.annotate(WatchTarget.class);
                        watchTargetAnnotation.param("value", target);
                    } else {
                        Log.warning("No watch target found for reference type field " + propertyName);
                    }
                } else {
                    Log.warning("No watch/target found on reference type field " + propertyName);
                }

                // annotate with label template to use from referenced field
                JsonNode enumSources = propertyNode.get("enumSource");
                if (enumSources != null && enumSources.isArray()) {
                    // expect DRIVER enumSources to be a list with single element
                    JsonNode enumSource = enumSources.get(0);
                    if (enumSource != null && enumSource.has("title")) {
                        String titlePattern = enumSource.get("title").asText();
                        if (titlePattern != null) {
                            JAnnotationUse titlePatternAnnotation = field.annotate(ReferenceTitlePattern.class);
                            titlePatternAnnotation.param("value", titlePattern);
                        } else {
                            Log.info("No title pattern set for reference type field " + propertyName);
                        }
                    } else {
                        Log.warning("No enumSource/title found for reference type field " + propertyName);
                    }
                } else {
                    Log.warning("No enumSources found for reference type field " + propertyName);
                }
            }
        }

        if (propertyNode.has("options")) {
            JsonNode options = propertyNode.get("options");
            if (options.has("hidden")) {
                Boolean isHidden = options.get("hidden").asBoolean();
                JAnnotationUse hiddenAnnotation = field.annotate(IsHidden.class);
                hiddenAnnotation.param("value", isHidden);
            }
        }

        if (propertyNode.has("description")) {
            String description = propertyNode.get("description").asText();

            if (!description.isEmpty()) {
                JAnnotationUse descriptionAnnotation = field.annotate(Description.class);
                descriptionAnnotation.param("value", description);
            }
        }

    }

    @Override
    public boolean isAdditionalPropertiesSupported() {
        return true;
    }
}
