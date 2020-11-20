import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * 文件操作相关的方法类
 *
 * @author csh
 */
public class FileUtil {

    /**
     * 获取target文件夹下classes与test-classes下的所有.class文件
     *
     * @param path target文件夹的路径
     * @return 包含所有.class文件的数组
     */
    public static ArrayList<File> getClassFiles(String path) {
        //生成完整路径
        File classesDirectory = new File(path + "/classes/");
        File testClassesDirectory = new File(path + "/test-classes/");
        //获取文件列表
        ArrayList<File> classesList = new ArrayList<>();
        ArrayList<File> testClassesList = new ArrayList<>();
        //读取所有文件
        readFiles(classesDirectory.listFiles(), classesList);
        readFiles(testClassesDirectory.listFiles(), testClassesList);
        //拼接
        ArrayList<File> result = new ArrayList<>();
        result.addAll(classesList);
        result.addAll(testClassesList);
        return result;
    }

    /**
     * 获取所有测试类的名称
     *
     * @param path target文件夹路径
     * @return 测试类文件名列表
     */
    public static ArrayList<String> getTestFileNames(String path) {
        //生成完整路径
        File testClassesDirectory = new File(path + "/test-classes/");
        //获取文件列表
        ArrayList<File> testClassesList = new ArrayList<>();
        //读取所有文件
        readFiles(testClassesDirectory.listFiles(), testClassesList);
        //最终结果记录的是去掉后缀的文件名，不含包名
        ArrayList<String> result = new ArrayList<>();
        for (File file : testClassesList) {
            result.add(file.getName().split("\\.")[0]);
        }
        return result;
    }

    /**
     * 递归读取文件夹中的文件到result中
     *
     * @param files  文件夹子目录列表
     * @param result 存储的结果
     */
    private static void readFiles(File[] files, ArrayList<File> result) {
        for (File file : files) {
            if (file.isDirectory()) {
                readFiles(file.listFiles(), result);
            } else {
                result.add(file);
            }
        }
    }

    /**
     * @param result 测试选择的结果
     * @param grain  粒度
     * @throws IOException
     */
    public static void resultToFile(HashSet<String> result, String grain) throws IOException {
        BufferedWriter out = new BufferedWriter(new FileWriter("./selection-" + grain + ".txt"));
        for (String s : result) {
            out.write(s + "\n");
        }
        out.close();
    }

}
