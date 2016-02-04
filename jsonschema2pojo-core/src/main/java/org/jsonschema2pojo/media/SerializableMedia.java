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

package org.jsonschema2pojo.media;

import android.util.Base64;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * Holder for media with Gson serializer adapters to either read/write path to media, or the media itself.
 *
 * @author Kathryn Killebrew
 */
public class SerializableMedia {
    public byte[] data; // base64-encoded media
    public String path; // URI

    /**
     * Reads/writes {@link SerializableMedia} via Gson as byte array.
     *
     * When reading, expects JSON value to be a byte array to set on {@link SerializableMedia#data} field.
     * When writing, will use {@link SerializableMedia#data} if set; if not, it will attempt to resolve the
     * URI in {@link SerializableMedia#path} if set
     */
    public static class SerializableMediaByteArrayAdapter extends TypeAdapter<SerializableMedia> {
        public SerializableMedia read(JsonReader reader) throws IOException {
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull();
                return null;
            }
            SerializableMedia newMedia = new SerializableMedia();
            newMedia.data = Base64.decode(reader.nextString(), Base64.NO_WRAP);
            return newMedia;
        }

        public void write(JsonWriter writer, SerializableMedia value) throws IOException {
            if (value == null) {
                writer.nullValue();
                return;
            }

            // just use data field directly if set
            if (value.data != null) {
                writer.value(Base64.encodeToString(value.data, Base64.NO_WRAP));
            }

            // attempt to read in image at path into data
            if (value.path != null && !value.path.isEmpty()) {
                File file = new File(value.path);
                writer.value(Base64.encodeToString(FileUtils.readFileToByteArray(file), Base64.NO_WRAP));
                return;
            }

            writer.nullValue();
        }
    }

    /**
     * Reads/writes {@link SerializableMedia} via Gson as String that is a URI pointing to the media.
     * Ignores the {@link SerializableMedia#data} field.
     *
     * When reading, expects JSON value to be a URI to set on the {@link SerializableMedia#path} field.
     * When writing, writes string from {@link SerializableMedia#path} field, if set.
     */
    public static class SerializableMediaPathStringAdapter extends TypeAdapter<SerializableMedia> {
        public SerializableMedia read(JsonReader reader) throws IOException {
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull();
                return null;
            }
            SerializableMedia newMedia = new SerializableMedia();
            newMedia.path = reader.nextString();
            return newMedia;
        }

        public void write(JsonWriter writer, SerializableMedia value) throws IOException {
            if (value == null || value.path == null || value.path.isEmpty()) {
                writer.nullValue();
                return;
            }

            writer.value(value.path);
        }
    }
}
