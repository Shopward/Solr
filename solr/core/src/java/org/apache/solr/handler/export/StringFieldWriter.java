/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.handler.export;

import java.io.IOException;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.CharsRefBuilder;
import org.apache.solr.common.MapWriter;
import org.apache.solr.common.util.ByteArrayUtf8CharSequence;
import org.apache.solr.common.util.JavaBinCodec;
import org.apache.solr.schema.FieldType;
import org.apache.solr.search.DocValuesIteratorCache;

class StringFieldWriter extends FieldWriter {
  protected final String field;
  private final FieldType fieldType;
  private BytesRef lastRef;
  private int lastOrd = -1;
  private final DocValuesIteratorCache.FieldDocValuesSupplier docValuesCache;

  protected CharsRefBuilder cref = new CharsRefBuilder();
  final ByteArrayUtf8CharSequence utf8 =
      new ByteArrayUtf8CharSequence(new byte[0], 0, 0) {
        @Override
        public String toString() {
          String str = super.utf16;
          if (str != null) return str;
          fieldType.indexedToReadable(new BytesRef(super.buf, super.offset, super.length), cref);
          str = cref.toString();
          super.utf16 = str;
          return str;
        }
      };

  public StringFieldWriter(
      String field,
      FieldType fieldType,
      DocValuesIteratorCache.FieldDocValuesSupplier docValuesCache) {
    this.field = field;
    this.fieldType = fieldType;
    this.docValuesCache = docValuesCache;
  }

  @Override
  public boolean write(
      SortDoc sortDoc, LeafReaderContext readerContext, MapWriter.EntryWriter ew, int fieldIndex)
      throws IOException {
    StringValue stringValue = (StringValue) sortDoc.getSortValue(this.field);
    BytesRef ref = null;

    if (stringValue != null) {
      /*
       We already have the top level ordinal used for sorting.
       Now let's use it for caching the BytesRef so we don't have to look it up.
       When we have long runs of repeated values do to the sort order of the docs this is a huge win.
      */

      if (stringValue.currentOrd == -1) {
        // Null sort value
        return false;
      }

      if (this.lastOrd == stringValue.currentOrd) {
        ref = lastRef;
      }

      this.lastOrd = stringValue.currentOrd;
    }

    if (ref == null) {
      SortedDocValues vals =
          docValuesCache.getSortedDocValues(
              sortDoc.docId, readerContext.reader(), readerContext.ord);
      if (vals == null) {
        return false;
      }

      int ord = vals.ordValue();
      ref = vals.lookupOrd(ord);

      if (stringValue != null) {
        // Don't need to set the lastRef if it's not a sort value.
        lastRef = ref.clone();
      }
    }

    writeBytes(ew, ref, fieldType);
    return true;
  }

  protected void writeBytes(MapWriter.EntryWriter ew, BytesRef ref, FieldType fieldType)
      throws IOException {
    if (ew instanceof JavaBinCodec.BinEntryWriter) {
      ew.put(this.field, utf8.reset(ref.bytes, ref.offset, ref.length, null));
    } else {
      fieldType.indexedToReadable(ref, cref);
      ew.put(this.field, cref.toString());
    }
  }
}
