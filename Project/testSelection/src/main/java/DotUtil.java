import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * .dot图相关操作的方法类
 *
 * @author csh
 */
public class DotUtil {

    /**
     * 初始化节点之间的连接关系
     *
     * @param nodes 记录所有的节点点及其targets
     * @return edges 连接关系
     */
    public static HashSet<HashMap<String, String>> initialEdges(ArrayList<String> nodes) {
        // 记录用于构建.dot文件的调用关系，里面的每一条是被调用者->调用者，注意名称
        HashSet<HashMap<String, String>> edges = new HashSet<HashMap<String, String>>();
        // 对每一个节点的调用关系进行分析
        for (String node : nodes) {

            //根据BasicCallGraph.nodeToString定义的形式进行分割，目的是把被调用者与调用者分开
            //[0] 位置是调用者，其他全是被调用者
            String[] nodeSplit = node.split("-");

            //去除所有带Primordial的节点,存入actualNodes
            List<String> actualNodes = new ArrayList<String>();
            for (String s : nodeSplit) {
                if (!s.contains("Primordial")) {
                    actualNodes.add(s);
                }
            }

            //防御式编程，剔除size不够的情况
            if (actualNodes.size() <= 1) {
                continue;
            }

            // 调用者
            String callNode = actualNodes.get(0).split(" >")[0].split("Application, ")[1];

            // 添加所有的边
            for (int i = 1; i < actualNodes.size(); i++) {
                // 获取被调用节点的名称，根据actualNodes内字符串的格式得出分割方法
                String calledNode = actualNodes.get(i).split(" >")[0].split("Application, ")[1];
                // 在节点之间添加边
                HashMap<String, String> edge = new HashMap<String, String>();
                edge.put(calledNode, callNode);
                edges.add(edge);
            }
        }
        return edges;
    }

    /**
     * 根据粒度来构建.dot图
     *
     * @param edges 连接关系
     * @param grain 粒度 分为class和method
     * @return .dot图
     */
    public static HashSet<String> buildDot(HashSet<HashMap<String, String>> edges, String grain) {
        //class粒度的dot图
        if ("class".equals(grain)) {
            return buildClassDot(edges);
        }
        //method粒度的dot图
        else {
            return buildMethodDot(edges);
        }
    }

    /**
     * 根据类粒度来构建.dot图
     *
     * @param edges 连接关系
     * @return .dot图
     */
    public static HashSet<String> buildClassDot(HashSet<HashMap<String, String>> edges) {
        HashSet<String> dotGraph = new HashSet<>();
        for (HashMap<String, String> map : edges) {
            for (String key : map.keySet()) {
                // 按照类级粒度分割字符串，去掉Ljava一类的方法
                // 结果格式如"net.mooctest.CMD.<init>()V" -> "net.mooctest.CMDTest1.test4()V"
                String value = map.get(key).split(", ")[0];
                String line = key.split(", ")[0] + " -> " + value;
                if (!line.contains("Ljava")) {
                    dotGraph.add(line);
                }
            }
        }
        return dotGraph;
    }

    /**
     * 根据方法粒度来构建.dot图
     *
     * @param edges 连接关系
     * @return .dot图
     */
    public static HashSet<String> buildMethodDot(HashSet<HashMap<String, String>> edges) {
        HashSet<String> dotGraph = new HashSet<String>();
        for (HashMap<String, String> map : edges) {
            for (String key : map.keySet()) {
                String value = map.get(key);
                // 根据innerClassName 来获取包名
                String packageNameKey = key.split(", ")[0].replace("/", ".").substring(1);
                String packageNameValue = value.split(", ")[0].replace("/", ".").substring(1);
                // 修改key 与 value的内容，使其符合输出格式
                key = key.split(", ")[0] + " " + packageNameKey + "." + key.split(", ")[1];
                value = value.split(", ")[0] + " " + packageNameValue + "." + value.split(", ")[1];
                String line = key + " -> " + value;
                //只有两个端点开头都不是Ljava的才算作是图中的节点
                if (!key.startsWith("Ljava") && !value.startsWith("Ljava")) {
                    dotGraph.add(line);
                }
            }
        }

        return dotGraph;
    }

    /**
     * 将.dot图输出到文件
     *
     * @param targetPath target文件夹路径
     * @param grain      粒度
     * @param dotGraph   .dot图
     * @throws IOException
     */
    public static void writeToFile(String targetPath, String grain, ArrayList<String> dotGraph) throws IOException {
        //创建文件
        String[] split = targetPath.split("\\\\");
        String fileName = split[split.length - 2];
        BufferedWriter out = new BufferedWriter(new FileWriter(grain + "-" + fileName.split("-")[1] + ".dot"));
        out.write("digraph " + fileName.split("-")[1].toLowerCase() + "_" + grain + " {\n");
        //分粒度进行输出
        for (String s : dotGraph) {
            String calledNode = s.split(" -> ")[0];
            String callNode = s.split(" -> ")[1];
            if ("class".equals(grain)) {
                out.write("\t" + "\"" + calledNode + "\"" + " -> " + "\"" + callNode + "\"" + ";\n");
            } else {
                calledNode = calledNode.split(" ")[1];
                callNode = callNode.split(" ")[1];
                out.write("\t" + "\"" + calledNode + "\"" + " -> " + "\"" + callNode + "\"" + ";\n");
            }
        }
        out.write("}");
        out.close();
        System.out.println(fileName + "Write Success");
    }


    /**
     * 在.dot图上找到changeInfo的闭包
     *
     * @param dotGraph    .dot图
     * @param changeInfos .变更信息
     * @param grain       粒度
     * @return 闭包
     */
    public static HashSet<String> findClosure(ArrayList<String> dotGraph, ArrayList<String> changeInfos, String grain) {
        if ("class".equals(grain)) {
            return findClassClosure(dotGraph, changeInfos);
        } else {
            return findMethodClosure(dotGraph, changeInfos);
        }
    }

    /**
     * 方法粒度
     * 在.dot图上找到changeInfo的闭包
     *
     * @param dotGraph    .dot图
     * @param changeInfos .变更信息
     * @return 闭包
     */
    private static HashSet<String> findMethodClosure(ArrayList<String> dotGraph, ArrayList<String> changeInfos) {
        HashSet<String> closure = new HashSet<>();
        int closureSize = 0;
        // 第一次找闭包
        for (String changeInfo : changeInfos) {
            // dotGraph的每一行被调用者->调用者
            for (String line : dotGraph) {
                //line的格式是 类名A 方法a -> 类名B 方法b
                String calledNode = line.split(" -> ")[0];
                String callNode = line.split(" -> ")[1];
                if (calledNode.equals(changeInfo)) {
                    // 如果被调用者发生了变更，那么加入调用者
                    closure.add(calledNode);
                    closure.add(callNode);
                }
            }
        }

        while (closureSize != closure.size()) {
            //用于存储node，因为遍历的时候不能同时添加元素
            HashSet<String> nodeStore = new HashSet<String>();
            closureSize = closure.size();
            // 对闭包中现有的元素再去找闭包，直到闭包的大小不会再增加
            for (String c : closure) {
                for (String line : dotGraph) {
                    String calledNode = line.split(" -> ")[0];
                    String callNode = line.split(" -> ")[1];
                    if (calledNode.equals(c)) {
                        // 如果被调用者发生了变更，那么加入调用者
                        nodeStore.add(calledNode);
                        nodeStore.add(callNode);
                    }
                }
            }
            closure.addAll(nodeStore);
        }

        return closure;
    }

    /**
     * 类粒度
     * 在.dot图上找到changeInfo的闭包
     *
     * @param dotGraph    .dot图
     * @param changeInfos .变更信息
     * @return 闭包
     */
    private static HashSet<String> findClassClosure(ArrayList<String> dotGraph, ArrayList<String> changeInfos) {
        HashSet<String> closure = new HashSet<>();
        int closureSize = 0;
        // 第一次找闭包
        for (String changeInfo : changeInfos) {
            // dotGraph的每一行被调用者->调用者
            for (String line : dotGraph) {
                // 这里的node均是类名
                // line的格式是 类名A 方法a -> 类名B 方法b
                String calledNode = line.split(" -> ")[0].split(" ")[0];
                String callNode = line.split(" -> ")[1].split(" ")[0];
                if (calledNode.equals(changeInfo.split(" ")[0])) {
                    // 如果被调用者发生了变更，那么加入调用者
                    closure.add(calledNode);
                    closure.add(callNode);
                }
            }
        }

        while (closureSize != closure.size()) {
            //用于存储node，因为遍历的时候不能同时添加元素
            HashSet<String> nodeStore = new HashSet<String>();
            closureSize = closure.size();
            // 对闭包中现有的元素再去找闭包，直到闭包的大小不会再增加
            for (String c : closure) {
                for (String line : dotGraph) {
                    // 这里的node均是类名
                    String calledNode = line.split(" -> ")[0].split(" ")[0];
                    String callNode = line.split(" -> ")[1].split(" ")[0];
                    if (calledNode.equals(c)) {
                        // 如果被调用者发生了变更，那么加入调用者
                        nodeStore.add(calledNode);
                        nodeStore.add(callNode);
                    }
                }
            }
            closure.addAll(nodeStore);
        }

        return closure;
    }


}
