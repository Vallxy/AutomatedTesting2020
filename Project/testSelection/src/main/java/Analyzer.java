import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.callgraph.impl.BasicCallGraph;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.annotations.Annotation;
import com.ibm.wala.util.config.AnalysisScopeReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * 用于静态分析的类，含main入口
 *
 * @author csh
 */
public class Analyzer {

    public static void main(String[] args) {
        //粒度
        String grain;
        if ("-c".equals(args[0])) {
            grain = "class";
        } else {
            grain = "method";
        }
        //所有classFile，包括生产类和测试类
        ArrayList<File> classFiles = FileUtil.getClassFiles(args[1]);
        //change_info文件
        File change_info = new File(args[2]);
        //用于记录callGraph中的所有测试方法，根据@Test注解来判断
        HashSet<String> testMethods = new HashSet<>();
        //构建CallGraph
        try {
            AnalysisScope scope = AnalysisScopeReader.readJavaScope("scope.txt", new File("exclusion.txt"), Analyzer.class.getClassLoader());
            for (File f : classFiles) {
                scope.addClassFileToScope(ClassLoaderReference.Application, f);
            }

            //生成类层次关系对象
            ClassHierarchy cha = ClassHierarchyFactory.makeWithRoot(scope);

            //生成进入点
            AllApplicationEntrypoints entryPoints = new AllApplicationEntrypoints(scope, cha);
            AnalysisOptions option = new AnalysisOptions(scope, entryPoints);
            SSAPropagationCallGraphBuilder builder = Util.makeZeroCFABuilder(
                    Language.JAVA, option, new AnalysisCacheImpl(), cha, scope
            );


            //利用0-CFA算法构建调用图
            CallGraph cg = builder.makeCallGraph(option, null);

            //用于保存node信息
            ArrayList<String> nodes = new ArrayList<>();

            //遍历cg中所有的节点
            for (CGNode node : cg) {
                // node中包含了很多信息，包括类加载器、方法信息等，这里只筛选出需要的信息
                if (node.getMethod() instanceof ShrikeBTMethod) {
                    //node.getMethod()返回一个比较泛化的IMethod实例，不能获取到我们想要的信息
                    //一般地，本项目中所有和业务逻辑相关的方法都是ShrikeBTMethod对象
                    ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                    //使用Primordial类加载器加载的类都属于Java原生类，我们一般不关心。
                    if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                        //添加节点信息
                        nodes.add(BasicCallGraph.nodeToString(cg, node));
                        //如果方法的注解是@Test,则加入testMethods中
                        //注意此处的Annotation类是wala中的
                        Collection<Annotation> annotations = method.getAnnotations();
                        for (Annotation annotation : annotations) {
                            if (annotation.toString().contains("Lorg/junit/Test")) {
                                String signature = method.getSignature();
                                String innerClass = method.getDeclaringClass().getName().toString();
                                testMethods.add(innerClass + " " + signature);
                            }
                        }
                    }
                }
            }


            //记录用于构建.dot文件的调用关系，里面的每一条是被调用者->调用者，注意名称
            HashSet<HashMap<String, String>> edges = DotUtil.initialEdges(nodes);

            //构建.dot图
            HashSet<String> dotGraph_class = DotUtil.buildDot(edges, "class");
            HashSet<String> dotGraph_method = DotUtil.buildDot(edges, "method");
            ArrayList<String> result_class = new ArrayList<String>(dotGraph_class);
            ArrayList<String> result_method = new ArrayList<String>(dotGraph_method);
            Collections.sort(result_class);
            Collections.sort(result_method);

            //.dot写入文件
            //DotUtil.writeToFile(args[1], "class", result_class);
            //DotUtil.writeToFile(args[1], "method", result_method);

            //读取change_info
            ArrayList<String> changeInfos = new ArrayList<>();
            BufferedReader bf = new BufferedReader(new FileReader(change_info));
            String change_info_line;
            while ((change_info_line = bf.readLine()) != null) {
                changeInfos.add(change_info_line);
            }

            //按照不同粒度完成测试选择
            makeTestSelection(grain, result_class, result_method, changeInfos, args[1], testMethods);

        } catch (Exception e) {
            System.out.println("Exception");
            e.printStackTrace();
        }
    }

    /**
     * 测试选择
     *
     * @param grain          粒度
     * @param dotGraphClass  class粒度的dotGraph
     * @param dotGraphMethod method粒度的dotGraph
     * @param changeInfos    变更信息
     * @param path           target文件夹路径
     * @param testMethods    所有测试方法的集合
     */
    private static void makeTestSelection(String grain, ArrayList<String> dotGraphClass, ArrayList<String> dotGraphMethod, ArrayList<String> changeInfos, String path, HashSet<String> testMethods) {
        //以类为粒度的选择
        if ("class".equals(grain)) {
            makeTestSelectionByClass(dotGraphClass, dotGraphMethod, changeInfos, path, testMethods);
        }
        //以方法为粒度的选择
        else {
            makeTestSelectionByMethod(dotGraphMethod, changeInfos, testMethods);
        }
    }

    /**
     * 类粒度
     * 测试选择
     *
     * @param dotGraphClass  class粒度的dotGraph
     * @param dotGraphMethod method粒度的dotGraph
     * @param changeInfos    变更信息
     * @param path           target文件夹路径
     * @param testMethods    所有测试方法的集合
     */
    private static void makeTestSelectionByClass(ArrayList<String> dotGraphClass, ArrayList<String> dotGraphMethod, ArrayList<String> changeInfos, String path, HashSet<String> testMethods) {
        //获取所有测试类的名称
        ArrayList<String> testFileNames = FileUtil.getTestFileNames(path);
        //在以类为粒度的图上计算变更类的闭包
        HashSet<String> classClosure = DotUtil.findClosure(dotGraphClass, changeInfos, "class");
        //最终被选择的类应该是上面两者的交集
        HashSet<String> resultClass = new HashSet<>();
        //这里的testFileNames只是测试文件名 不含包名，而classClosure是包含包名的
        for (String s : classClosure) {
            for (String t : testFileNames) {
                if (s.contains(t)) {
                    resultClass.add(s);
                }
            }
        }

        // 存储最终结果
        HashSet<String> result = new HashSet<>();
        // 遍历整张类粒度的dotGraph
        for (String line : dotGraphMethod) {
            // 被调用者的类名
            String calledNode = line.split(" -> ")[0];
            String calledNodeClass = calledNode.split(" ")[0];
            // 调用者的类名
            String callNode = line.split(" -> ")[1];
            String callNodeClass = callNode.split(" ")[0];
            //如果一个node的类名属于测试类又属于受影响的类，且这个node的方法是测试方法，那么其就要被添加到结果中去
            if (resultClass.contains(calledNodeClass) && testMethods.contains(calledNode)) {
                result.add(calledNode);
            }
            if (resultClass.contains(callNodeClass) && testMethods.contains(callNode)) {
                result.add(callNode);
            }
        }
        try {
            FileUtil.resultToFile(result, "class");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * 方法粒度
     * 测试选择
     *
     * @param dotGraphMethod method粒度的dotGraph
     * @param changeInfos    变更信息
     * @param testMethods    所有测试方法的集合
     */
    private static void makeTestSelectionByMethod(ArrayList<String> dotGraphMethod, ArrayList<String> changeInfos, HashSet<String> testMethods) {
        HashSet<String> result = new HashSet<>();
        //在以方法为粒度的图上计算变更方法的闭包
        HashSet<String> closure = DotUtil.findClosure(dotGraphMethod, changeInfos, "method");
        //只筛选出那些测试方法
        for (String c : closure) {
            if (testMethods.contains(c)) {
                result.add(c);
            }
        }
        try {
            FileUtil.resultToFile(result, "method");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
