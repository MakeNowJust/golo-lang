/*
 * Copyright 2012-2014 Institut National des Sciences Appliquées de Lyon (INSA-Lyon)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.insalyon.citi.golo.doc;

import fr.insalyon.citi.golo.compiler.parser.ASTCompilationUnit;
import gololang.Predefined;

import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.TreeMap;
import java.util.Map;
import java.util.LinkedList;

public class CtagsProcessor extends AbstractProcessor {

  private final LinkedList<String> ctags = new LinkedList<>();
  private String file = "file";

  private void ctagsLine(String name, String address, String field) {
    ctags.add(String.format("%s\t%s\t%s;\"\t%s\tlanguage:golo\n", name, file, address, field));
  }

  private void ctagsModule(ModuleDocumentation module) {
    ctagsLine(module.moduleName(),
        "/^module[:blank:]+" + module.moduleName().replace(".", "\\.") + "$/",
        "p\tline:" + module.moduleDefLine());
  }

  private void ctagsFunction(FunctionDocumentation funct) {
    ctagsFunction(funct, "", false);
  }

  private void ctagsFunction(FunctionDocumentation funct, String parent, boolean named) {
    String address = String.format("/function[:blank:]+%s[:blank:]+=/", funct.name());

    StringBuilder signature = new StringBuilder("\tsignature:(");
    if (funct.arity() > 0) {
      signature.append(funct.argument(0));
      for (int i = 1; i < funct.arity(); i++) {
        signature.append(", ").append(funct.argument(i));
      }
      if (funct.varargs()) { signature.append("..."); }
    }
    signature.append(")");

    StringBuilder fields = new StringBuilder("f");
    fields.append("\tline:").append(funct.line());
    if (funct.local()) {
      fields.append("\taccess:private\tfile:");
    } else {
      fields.append("\taccess:public");
    }
    fields.append(signature);
    if (parent != "") {
      if (named) {
        fields.append("\taugmentation:").append(parent);
      } else {
        fields.append("\taugment:").append(parent);
      }
    }
    ctagsLine(funct.name(), address, fields.toString());
  }

  private void ctagsAugment(String name, int line) {
    ctagsLine(name,
        String.format("/^augment[:blank:]+%s/", name.replace(".","\\.")),
        String.format("a\tline:%s", line));
  }

  private void ctagsAugmentation(String name, int line) {
    ctagsLine(name,
        String.format("/^augmentation[:blank:]+%s[:blank:]+=[:blank:]+{/", name),
        String.format("na\tline:%s", line));
  }

  private void ctagsStruct(String name, int line) {
    ctagsLine(name,
        String.format("/^struct[:blank:]+%s[:blank:]+=/", name),
        String.format("s\tline:%s", line));
  }

  private void ctagsImport(String name, int line) {
    ctagsLine(name,
        String.format("/^import[:blank:]+%s/", name.replace(".","\\.")),
        String.format("i\tline:%s", line));
  }

  private void ctagsModState(String name, int line) {
    ctagsLine(name,
        String.format("(let|var)[:blank:]+%s[:blank:]+=/", name),
        String.format("v\taccess:private\tfile:\tline:%s", line));
  }

  private void ctagsStructMember(String struct, String member, int line) {
    StringBuilder fields = new StringBuilder("m");
    fields.append("\tline:").append(line);
    if (member.charAt(0) == '_') {
      fields.append("\taccess:private");
    } else {
      fields.append("\taccess:public");
    }
    fields.append("\tstruct:").append(struct);
    ctagsLine(member,
        String.format("/struct[:blank:]+%s[:blank:]+=/", struct),
        fields.toString());
  }

  private String ctagsAsString() {
    java.util.Collections.sort(ctags);
    StringBuilder buffer = new StringBuilder();
    for (String line : ctags) {
      buffer.append(line);
    }
    return buffer.toString();
  }

  @Override
  public String render(ASTCompilationUnit compilationUnit) throws Throwable {
    ModuleDocumentation documentation = new ModuleDocumentation(compilationUnit);
    ctagsModule(documentation);
    for (Map.Entry<String,Integer> imp : documentation.imports().entrySet()) {
      ctagsImport(imp.getKey(), imp.getValue());
    }
    for (StructDocumentation struct : documentation.structs()) {
      ctagsStruct(struct.name(), struct.line());
      for (String member : struct.members()) {
        ctagsStructMember(struct.name(), member, struct.line());
      }
    }
    for (NamedAugmentationDocumentation augment : documentation.namedAugmentations()) {
      ctagsAugmentation(augment.name(), augment.line());
      for (FunctionDocumentation funct : augment.functions()) {
        ctagsFunction(funct, augment.name(), true);
      }
    }
    for (AugmentationDocumentation augment : documentation.augmentations()) {
      ctagsAugment(augment.target(), augment.line());
      for (FunctionDocumentation funct : augment.functions()) {
        ctagsFunction(funct, augment.target(), false);
      }
    }
    for (Map.Entry<String,Integer> state : documentation.moduleStates().entrySet()) {
      ctagsModState(state.getKey(), state.getValue());
    }
    for (FunctionDocumentation funct : documentation.functions(true)) {
      ctagsFunction(funct);
    }
    return ctagsAsString();
  }

  @Override
  public void process(Map<String, ASTCompilationUnit> units, Path targetFolder) throws Throwable {
    Path targetFile = null;
    if (targetFolder.toString().equals("-")) {
      targetFile = targetFolder;
    } else {
      ensureFolderExists(targetFolder);
      targetFile = targetFolder.resolve("tags");
    }
    ctags.clear();
    for (String src : units.keySet()) {
      file = src;
      render(units.get(src));
    }
    Predefined.textToFile(ctagsAsString(), targetFile);
  }
}
