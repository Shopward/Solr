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
package org.apache.solr.request.macro;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.CollectionUtil;
import org.apache.solr.search.StrParser;
import org.apache.solr.search.SyntaxError;

public class MacroExpander {
  public static final String MACRO_START = "${";
  private static final int MAX_LEVELS = 25;

  private Map<String, String[]> orig;
  private Map<String, String[]> expanded;
  private String macroStart = MACRO_START;
  private char escape = '\\';
  private int level;
  private final boolean failOnMissingParams;

  public MacroExpander(Map<String, String[]> orig) {
    this(orig, false);
  }

  public MacroExpander(Map<String, String[]> orig, boolean failOnMissingParams) {
    this.orig = orig;
    this.failOnMissingParams = failOnMissingParams;
  }

  public static Map<String, String[]> expand(Map<String, String[]> params) {
    MacroExpander mc = new MacroExpander(params);
    mc.expand();
    return mc.expanded;
  }

  public boolean expand() {
    this.expanded = CollectionUtil.newHashMap(orig.size());

    boolean changed = false;
    for (Map.Entry<String, String[]> entry : orig.entrySet()) {
      String k = entry.getKey();
      String[] values = entry.getValue();
      if (!isExpandingExpr() && "expr".equals(k)) { // SOLR-12891
        expanded.put(k, values);
        continue;
      }
      String newK = expand(k);
      List<String> newValues = null;
      for (String v : values) {
        String newV = expand(v);
        if (!Objects.equals(newV, v)) {
          if (newValues == null) {
            newValues = new ArrayList<>(values.length);
            for (String vv : values) {
              if (Objects.equals(vv, v)) break;
              newValues.add(vv);
            }
          }
        }
        if (newValues != null) {
          newValues.add(newV);
        }
      }

      if (newValues != null) {
        values = newValues.toArray(new String[0]);
        changed = true;
      }

      if (!Objects.equals(k, newK)) {
        changed = true;
      }

      expanded.put(newK, values);
    }

    return changed;
  }

  private Boolean isExpandingExpr() {
    return Boolean.valueOf(System.getProperty("StreamingExpressionMacros", "false"));
  }

  public String expand(String val) {
    level++;
    try {
      if (level >= MAX_LEVELS) {
        throw new SolrException(
            SolrException.ErrorCode.BAD_REQUEST,
            "Request template exceeded max nesting of " + MAX_LEVELS + " expanding '" + val + "'");
      }
      return _expand(val);
    } finally {
      level--;
    }
  }

  private String _expand(String val) {
    // quickest short circuit
    int idx = val.indexOf(macroStart.charAt(0));
    if (idx < 0) return val;

    int start = 0; // start of the unprocessed part of the string
    StringBuilder sb = null;
    for (; ; ) {
      assert idx >= start;
      idx = val.indexOf(macroStart, idx);

      // check if escaped
      if (idx > 0) {
        // check if escaped...
        // TODO: what if you *want* to actually have a backslash... perhaps that's when we allow
        // changing of the escape character?

        char ch = val.charAt(idx - 1);
        if (ch == escape) {
          idx += macroStart.length();
          continue;
        }
      } else if (idx < 0) {
        break;
      }

      // found unescaped "${"
      final int matchedStart = idx;

      int rbrace = val.indexOf('}', matchedStart + macroStart.length());
      if (rbrace == -1) {
        // no matching close brace...
        if (failOnMissingParams) {
          return null;
        }
        break;
      }

      if (sb == null) {
        sb = new StringBuilder(val.length() * 2);
      }

      if (matchedStart > 0) {
        sb.append(val, start, matchedStart);
      }

      // update "start" to be at the end of ${...}
      idx = start = rbrace + 1;

      // String in-between braces
      StrParser parser = new StrParser(val, matchedStart + macroStart.length(), rbrace);
      try {
        String paramName = parser.getId();
        String defVal = null;
        boolean hasDefault = parser.opt(':');
        if (hasDefault) {
          defVal = val.substring(parser.pos, rbrace);
        }

        // in the event that expansions become context dependent... consult original?
        String[] replacementList = orig.get(paramName);

        // TODO - handle a list somehow...
        String replacement = replacementList != null ? replacementList[0] : defVal;
        if (replacement != null) {
          String expandedReplacement = expand(replacement);
          if (failOnMissingParams && expandedReplacement == null) {
            return null;
          }
          sb.append(expandedReplacement);
        } else if (failOnMissingParams) {
          return null;
        }

      } catch (SyntaxError syntaxError) {
        if (failOnMissingParams) {
          return null;
        }
        // append the part we would have skipped
        sb.append(val, matchedStart, start);
      }
    } // loop idx

    if (sb == null) {
      return val;
    }
    sb.append(val, start, val.length());
    return sb.toString();
  }
}
