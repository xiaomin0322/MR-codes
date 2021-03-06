package com.emar.recsys.user.util.mr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import oracle.net.aso.p;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.web.resources.Param;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.log4j.Logger;

import com.emar.recsys.user.util.DateParse;
import com.emar.util.Ip2AreaUDF;
import com.google.common.io.Closeables;

/**
 * 
 * @author zhou 重用常见 IO 操作
 */
public class HdfsIO {
	private static Logger log = Logger.getLogger(HdfsIO.class);
	private static final String S_MAP = "\t", S_NEWLINE = "\n";

	/**
	 * @param path
	 * @param c
	 *            input as this.getClass()
	 * @return 读取 本地文件 or 包中的资源文件 or Global 文件的内容返回
	 */
 	public static List<String> readFile(Class c, String path) {
		if (path == null) {
			return null;
		}
		ArrayList<String> lines = new ArrayList<String>();
		BufferedReader buf;
		String line;

		try {
			try {
				buf = new BufferedReader(new FileReader(new File(path)));
				while ((line = buf.readLine()) != null) {
					lines.add(line);
				}
				buf.close();
			} catch (FileNotFoundException e2) {
				InputStream ins = c.getResourceAsStream(path);
				buf = new BufferedReader(new InputStreamReader(ins));
				while ((line = buf.readLine()) != null) {
					lines.add(line);
				}
				buf.close();
			}

		} catch (IOException e) {
			e.printStackTrace();
			lines.clear();
			// load Global file
			FSDataInputStream in = null;
			Configuration conf = new Configuration();
			FileSystem fs = null;

			try {
				fs = FileSystem.get(URI.create(path), conf);
				in = fs.open(new Path(path));
				while ((line = in.readLine()) != null) {
					lines.add(line);
				}
			} catch (IOException e1) {
				e1.printStackTrace();
			} finally {
				IOUtils.closeStream(in);
			}
		}

		return lines;
	}
	
 	/**
 	 * 将Map对象写到HDFS 
 	 */
	public static void writeMap(Map data, Path path, Configuration configuration) throws IOException {
	    FileSystem fs = FileSystem.get(path.toUri(), configuration);
	    FSDataOutputStream out = fs.create(path);
	    try {
	    	Set keys = data.keySet();
	    	for (Object oi: keys) {
	    		out.writeChars(oi.toString());
	    		out.writeChars(S_MAP);
	    		out.writeChars(data.get(oi).toString());
	    		out.writeChars(S_NEWLINE);
	    	}
	    } finally {
	    	Closeables.close(out, false);
	    }
	}

	/**
	 * 打印 MR 的有效输入文件
	 * 
	 * @throws IOException
	 */
	public static void printInput(Job job) throws IOException {
		int fcnt = 0;
		Path[] input_tot = FileInputFormat.getInputPaths(job);

		for (Path p : input_tot) {
			FileSystem fsystem;
			// try { // 不处理异常，出错则终止程序
			fsystem = p.getFileSystem(job.getConfiguration());
			if (fsystem.isFile(p)) {
				fcnt += 1;
				log.info("input path: " + p);
			} else {
				FileStatus[] input_fs = fsystem.globStatus(p);
				if (input_fs == null) {
					log.warn("bad input path: " + p);
					continue;
				}
				for (FileStatus ps : input_fs) {
					fcnt += 1;
					log.info("input path: " + ps.getPath());
				}
			}
			// } catch (IOException e) {
			// }
		}
		log.info("total input file number=" + fcnt);
		return;
	}

	/**
	 * 设置 MR 的输入文件路径
	 * 
	 * @throws IOException
	 * @param range
	 *            时间范围yyyyMMddHH_yyyyMMddHH
	 * @param timeFMT
	 *            时间在路径中的格式
	 * @param pathFMT
	 *            完整的路径格式
	 */
	public static boolean setInput(String range, String timeFMT,
			String pathFMT, Job job) throws IOException {
		String[] datapath = DateParse.getRange(range, timeFMT);
		if (datapath == null) {
			throw new IOException("date range parse failed.");
		}

		String fpath;
		FileSystem fs = FileSystem.get(job.getConfiguration());
		FileStatus[] a_fs;
		for (String s : datapath) {
			fpath = String.format(pathFMT, s);
			try { // 可能文件不存在
				Path npi = new Path(fpath);
				a_fs = fs.globStatus(npi);
				if (a_fs != null && a_fs.length != 0) {
					FileInputFormat.addInputPath(job, npi);
					log.info("set input path pattern: " + fpath);
				}
			} catch (IOException e) {
				log.error("JOB add input exception. [MSG]: " + e.getMessage());
			}
		}
		printInput(job);

		return true;
	}

	/** 删除当前目录|文件， 如果父级目录不存在则创建。 */
	public static boolean removeDir(String path, Job job) throws IOException {
		FileSystem fs = FileSystem.get(job.getConfiguration());
		Path testPath = new Path(path);
		try {
			if (fs.getFileStatus(testPath).isDir() || fs.isFile(testPath)) {
				fs.delete(testPath, true);
				log.info("remove path: " + testPath);
			} else if (!fs.getFileStatus(testPath.getParent()).isDir()) {
				fs.mkdirs(testPath); // 父目录不存在则创建目录结构
				fs.delete(testPath, true);
				log.info("create parent dir before path: " + testPath);
			}
		} catch (IOException e) {
			log.error("HDFS path operator failed. [MSG]: " + e.getMessage());
		}
		return true;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// List<String> localData = HdfsIO.readFile(null, "d:/Data/.gitignore");
		// System.out.print("[Info] local file read size: " + localData.size()
		// + "\nfrom-args\t" + HdfsIO.readFile(null, args[0]).size());
		Configuration conf = new Configuration();
		Map<Integer, String> data = new HashMap<Integer, String>();
		data.put(1, "a");
		String opath = "/recommend/user/zhouliaoming/output/write";

		try {
			writeMap((Map)data, new Path(opath), conf);
			System.exit(0);
			
			String s = "resource/classify/firstcate";
			List<String> lines = HdfsIO.readFile(HdfsIO.class, s);
			System.out.println("[Info] resource file read size: " + lines.size()
					+ "\nclass-load: " + HdfsIO.class.getResource(s) + "\ncload2： "
					+ HdfsIO.class.getResource("/com/emar/util/" + s)
					+ "\ncload3: "
					+ Ip2AreaUDF.class.getResource("resource/ip_dstc_ne.dat"));
			
			String range = "2013061800_2013061900", tfmt = "yyyyMMdd", fmt = "/data/stg/s_order_log/%s/1/*.dat";
			HdfsIO.setInput(range, tfmt, fmt, new Job());
			System.out.println("from CMD");
			HdfsIO.setInput(args[0], args[1], args[2], new Job());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
