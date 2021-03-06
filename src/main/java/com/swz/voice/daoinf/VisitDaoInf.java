package com.swz.voice.daoinf;

import java.util.List;

import com.swz.data.vo.mysql.Log_visit;
import com.swz.voice.model.PageInfo;
import com.swz.voice.model.VisitInfo;

/**
 * 回访日志信息
 * 
 * @author Charsp
 *
 * @date 2015年1月30日
 */
public interface VisitDaoInf {
	// 增加日志
	public Integer saveVisit(Log_visit visit);

	// 查询日志
	public List<Log_visit> getVisits(PageInfo pageInfo);

	// 获取语音系统信息
	public List<VisitInfo> getVisitInfos(Integer id);

}
