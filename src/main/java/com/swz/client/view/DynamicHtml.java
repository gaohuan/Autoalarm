package com.swz.client.view;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/***
 * 使用java反射机制编写的动态Html报表类
 * 
 * @author Charsp
 *
 * @date 2015年2月16日
 */
public class DynamicHtml {
	/**
	 * 生成带分页的Table表单，支持打印，彩色图输出
	 * 
	 * @param modelList
	 * @param headMap
	 * @return
	 */
	public StringBuffer htmlDivTable(List<?> modelList,
			Map<String, Map<String, String>> headMap) {
		// html 系统头打印
		StringBuffer htmlTable = htmlHeadBuffer(headMap);
		// body
		htmlTable.append("<body>");
		// swz 报表打印
		htmlTable.append("<H1>SWZ REPORTER AND PRINT</H1>");
		htmlTable.append("");
		// 任意取一个类
		Field[] fields = modelList.get(0).getClass().getDeclaredFields();
		// 报表头
		for (int i = 0; i < fields.length; i++) {

		}
		// 二维列表值
		for (int i = 0; i < modelList.size(); i++) {
			for (int j = 0; j < fields.length; j++) {

			}
		}
		// table 标题
		return htmlTable;
	}

	/**
	 * html网页的头信息
	 * 
	 * @param headMap
	 *            js 使用script作为标签 css,img使用link作为标签
	 * @return
	 * 
	 * @describe substance 内容标签
	 */
	private StringBuffer htmlHeadBuffer(Map<String, Map<String, String>> headMap) {
		StringBuffer htmlHead = new StringBuffer();
		// script解析js style解析css 其他数据title,link,meta
		if (headMap != null) {
			String tempString = "";
			for (String key : headMap.keySet()) {
				Map<String, String> contextMap = headMap.get(key);
				htmlHead.append("<" + key + " ");
				// 填充属性数据
				for (String subKey : contextMap.keySet()) {
					if (subKey.equalsIgnoreCase("substance"))
						tempString = contextMap.get(subKey);
					else
						htmlHead.append(subKey + "=\"" + contextMap.get(subKey)
								+ "\" ");
				}
				// 复值给脚本
				if (key.equalsIgnoreCase("script")
						|| key.equalsIgnoreCase("style")
						|| key.equalsIgnoreCase("title")) {
					htmlHead.append(" >");// 属性结束符
					// 内容开始
					htmlHead.append(tempString);
					htmlHead.append("</" + key + ">");// 标签结束符
				}
				// 纯属性复值
				else
					htmlHead.append(" />");// 属性结束符
			}
		}
		return htmlHead;
	}

	public String htmlOnePage() throws UnsupportedEncodingException {
		StringBuffer htmlTable = new StringBuffer();
		htmlTable.append("<!DOCTYPE html PUBLIC " + "\"-//W3C//DTD HTML 4.01 "
				+ "Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd"
				+ "\">");
		// html 系统头打印
		htmlTable.append("<html>");
		htmlTable.append("<head>");
		htmlTable.append("<meta http-equiv=\"Content-Type\" content="
				+ "\"text/html; charset=UTF-8\">");
		htmlTable.append("</head>");
		// /body
		htmlTable.append("</body>");
		htmlTable.append("</html>");
		return new String(htmlTable.toString().getBytes(), "iso-8895-1");
	}
}