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

import android.graphics.BitmapFactory;
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

            // attempt to read in image at path into data field
            boolean readFromPath = false;
            if (value.data == null && value.path != null && !value.path.isEmpty()) {
                readFromPath = true;
                File file = new File(value.path);
                value.data = FileUtils.readFileToByteArray(file);
            }

            if (value.data != null) {
                // peek at image metadata to get mime type
                BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                bmOptions.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(value.data, 0, value.data.length, bmOptions);
                String mimeType = bmOptions.outMimeType;

                // handle unknown mime type, or error getting it
                if (mimeType == null) {
                    mimeType = "image/jpeg";
                }

                // prepend byte string with URI scheme, like: data:image/jpeg;base64,
                writer.value("data:" + mimeType + ";base64," + Base64.encodeToString(value.data, Base64.NO_WRAP));

                writer.flush(); // force an output stream flush after serializing media

                // if bytes were read in from file for this serialization, clear them back out when done
                if (readFromPath) {
                    value.data = null;
                }
            } else {
                writer.nullValue();
            }
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
