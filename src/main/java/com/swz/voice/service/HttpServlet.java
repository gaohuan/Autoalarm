package com.swz.voice.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 一个基本 http 的Post(JSon)和Get发送helper
 * 
 * @author Charsp
 * @date 2015-02-25
 */
public class HttpServlet {
	private static Logger log = LoggerFactory.getLogger(HttpServlet.class);

	public static String sendGet(String url, String param, String encode) {
		String result = "";
		BufferedReader in = null;
		try {
			String urlNameString = url;
			if (param != null)
				urlNameString = url + "?" + param;
			// System.out.println(urlNameString);
			URL realUrl = new URL(urlNameString);
			URLConnection connection = realUrl.openConnection();
			connection.setRequestProperty("accept", "*/*");
			connection.setRequestProperty("connection", "Keep-Alive");
			connection.setRequestProperty("user-agent",
					"Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
			connection.setConnectTimeout(3000);
			connection.setReadTimeout(3000);
			connection.connect();
			if (encode != null) {
				in = new BufferedReader(new InputStreamReader(
						connection.getInputStream(), encode));
			} else {
				in = new BufferedReader(new InputStreamReader(
						connection.getInputStream(), "UTF-8"));
			}
			String line;
			while ((line = in.readLine()) != null) {
				result += line;
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		} finally {
			try {
				if (in != null) {
					in.close();
				}
			} catch (Exception e2) {
				e2.printStackTrace();
			}
		}
		// log.info("SendGet返回的代码为：" + result);
		return result;
	}

	/**
	 * 
	 */
	public static String sendPost(String url, String param) {
		OutputStream out = null;
		BufferedReader in = null;
		String result = "";
		try {
			URL realUrl = new URL(url);
			// �򿪺�URL֮�������
			URLConnection conn = realUrl.openConnection();
			// // ����ͨ�õ���������
			// conn.setRequestProperty("accept", "*/*");
			// conn.setRequestProperty("connection", "Keep-Alive");
			// conn.setRequestProperty("user-agent",
			// "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
			conn.setRequestProperty("Content-Type", "application/json");
			// conn.setRequestProperty("Accept", "application/json");
			// 设置接收数据的格式
			// ����POST�������������������
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setUseCaches(false);
			// ��ȡURLConnection�����Ӧ�������
			// if (!param.equals("")) {
			out = conn.getOutputStream();
			// 默认编码便是"UTF-8"
			out.write(param.getBytes());
			// flush请求数据
			out.flush();
			// }
			// 由BufferedReader获取返回数据，默认编码为UTF-8
			in = new BufferedReader(new InputStreamReader(
					conn.getInputStream(), "UTF-8"));
			String line;
			while ((line = in.readLine()) != null) {
				result += line;
			}
		} catch (Exception e) {
			System.out.println("发送post异常" + e);
			e.printStackTrace();
		}
		// 强制关闭程序连接端口
		finally {
			try {
				if (out != null) {
					out.close();
				}
				if (in != null) {
					in.close();
				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		return result;
	}
}
