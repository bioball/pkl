/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pkl.core.ast.member;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.source.SourceSection;
import org.pkl.core.ast.ExpressionNode;

public class MemberBodyWrapperNode extends ExpressionNode {
  @Child ExpressionNode underlyingBodyNode;

  public MemberBodyWrapperNode(ExpressionNode underlyingBodyNode) {
    this.underlyingBodyNode = underlyingBodyNode;
  }

  @Override
  public SourceSection getSourceSection() {
    return underlyingBodyNode.getSourceSection();
  }

  // pretend this is a statement (it's actually not).
  @Override
  public boolean hasTag(Class<? extends Tag> tag) {
    return tag == StandardTags.StatementTag.class;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return underlyingBodyNode.executeGeneric(frame);
  }
}
