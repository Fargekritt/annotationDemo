package org.example.processor;

import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SupportedAnnotationTypes("org.example.annotation.BuilderProperty")
@SupportedSourceVersion(SourceVersion.RELEASE_19)
@AutoService(Processor.class)
public class BuilderProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);

            Map<Boolean, List<Element>> annotatedMethods = annotatedElements.stream().collect(
                    Collectors.partitioningBy(element ->
                            ((ExecutableType) element.asType()).getParameterTypes().size() == 1 &&
                                    element.getSimpleName().toString().startsWith("set")));

            List<Element> setters = annotatedMethods.get(true);
            List<Element> otherMethods = annotatedMethods.get(false);

            otherMethods.forEach(element ->
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "@BuilderProperty must be used on setter methods that start with set " +
                                    "with a single argument", element));
            if (setters.isEmpty())
                continue;

            String className = ((TypeElement) setters.get(0).getEnclosingElement()).getQualifiedName().toString();

            Map<String, String> setterMap = setters.stream().collect(Collectors.toMap(
                    setter -> setter.getSimpleName().toString(),
                    setter -> ((ExecutableType) setter.asType()).getParameterTypes().get(0).toString()
            ));

            try {
                writeBuilderFile(className, setterMap);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return true;
    }

    private void writeBuilderFile(String className, Map<String, String> setterMap) throws IOException {
        String packageName = null;
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            packageName = className.substring(0, lastDot);
        }

        //Create names
        String simpleClassName = className.substring(lastDot + 1);
        String builderClassName = className + "Builder";
        String builderSimpleClassName = builderClassName.substring(lastDot + 1);

        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(builderClassName);

        //Writing the file itself
        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {

            //Write the top package name
            if(packageName != null){
                out.println("package " + packageName + ";");
                out.println();
            }

            //Write the class declaration
            out.println("public class " + builderSimpleClassName + " {");
            out.println();

            //Create the field to hold the object that is building
            out.println("    private " + simpleClassName + " object = new " + simpleClassName + "();");
            out.println();

            //Create the build method
            out.println("    public " + simpleClassName + " build() {");
            out.println("        return object;");
            out.println("    }");
            out.println();

            setterMap.entrySet().forEach(setter -> {
                String methodName = setter.getKey();
                String argumentType = setter.getValue();

                out.println("    public " + builderSimpleClassName + " " + methodName + "(" + argumentType + " value) {");
                out.println("        object." + methodName + "(value);");
                out.println("        return this;");
                out.println("    }");
                out.println();
            });

            out.println("}");
        }
    }
}
