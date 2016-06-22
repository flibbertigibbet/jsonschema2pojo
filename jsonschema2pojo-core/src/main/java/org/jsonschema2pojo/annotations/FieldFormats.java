package org.jsonschema2pojo.annotations;

/**
 * For annotating with JSON schema field formats, as used by json-editor.
 *
 * Created by kat on 6/22/16.
 */
public enum FieldFormats {
    // text type formats
    color, date, datetime, datetimelocal, email, html, month, number, range, tel, text, textarea, time, url, week,
    // other type formats
    checkbox, table, select, grid, location
}
