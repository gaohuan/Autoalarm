package com.swz.voice.service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.swz.data.vo.mysql.Log_alarm;
import com.swz.data.vo.mysql.Log_visit;
import com.swz.voice.impl.SHT_PortTread;
import com.swz.voice.manager.ConfigDatas;
import com.swz.voice.model.PaperResult;
import com.swz.voice.model.QuestionResult;
import com.swz.voice.model.SHP_A3;
import com.swz.voice.model.VisitPhase;

/**
 * 板卡事务处理
 * 
 * @author Charsp
 *
 * @date 2015年1月9日
 */
public class TransactionStream extends Thread {
	// 公共变量
	// public int invailablePort = 0;
	// 线程结束代码
	public boolean isThreadStop = false;
	// 私有变量
	private final String GETPATH = ConfigDatas.getGetPath();
	private final static String PUTPATH = ConfigDatas.getPutPath();
	private final String MYINF_USER = "vehiclenum=VEHICLENUM&sign=info&objectid=OBJECTID";
	private final String MYINFAddress = ConfigDatas.getMYINFAddress();
	private static JSONObject alarmSignal = null;
	/**
	 * 当前大约延时(秒)
	 */
	private static int currSecond = 0;
	/**
	 * 端口忙标识
	 */
	private static Map<String, Long> repeatAlarm = new HashMap<String, Long>();

	// 原始数据一条
	private void getHttpData() {
		// 报警信息
		JSONObject alarm = new JSONObject();
		// 获取数据
		try {
			JSONObject jsonObject = JSONObject.fromObject(HttpServlet.sendGet(
					GETPATH, null, null));
			// log.info("端口" + currPort + "获取到数据" + jsonObject);
			if (jsonObject != null && jsonObject.getString("ret").equals("0")) {
				// log.info("报警信息是：" + jsonObject);
				// 报警信息
				alarm = jsonObject.getJSONArray("list").getJSONObject(0);
				String code = alarm.getString("code");
				// 放走紧急报警(车辆劫持)和碰撞报警的code值
				// 堆栈已满直接 发送
				if (ConfigDatas.getAlarmStyle().contains(code)
						|| alarmSignal != null) {
					if (alarmSignal != null)
						alarm.put("msg", "当前alarmSignal 不为空值！");
					else
						alarm.put("msg", "当前报警类型被过滤！");
					sendFailInfo(alarm, true);
					return;
				}
				// 车辆信息
				JSONObject carInfo = jsonObject.getJSONObject("car");
				// 中文空格UTF-8编码发送
				String paramString = MYINF_USER
						.replace(
								"VEHICLENUM",
								URLEncoder.encode(carInfo.getString("car_no"),
										"UTF-8")).replace("OBJECTID",
								carInfo.getString("vehideid"));
				// log.info("参数为：" + paramString);
				paramString = HttpServlet.sendGet(MYINFAddress, paramString,
						null);
				// log.info("返回的数据为" + paramString);

				String telePhone = JSONObject.fromObject(paramString)
						.getString("AlldayTel1");

				// log.info("获得用户电话为：" + telePhone);
				// 调试模式使用我的手机号码
				if (ConfigDatas.isDebug()) {
					// log.info("调试将电话" + telePhone + "修改为13702271353");
					telePhone = "13702271353";
				}
				// 电话号码会有乱码
				Pattern p = Pattern.compile("^1[3|5|7|8|][0-9]{9}$");
				Matcher m = p.matcher(telePhone);
				// 电话号码匹配成功
				if (m.matches()) {
					// 必须加9
					telePhone = "9" + isTelephone(telePhone);

					// 判断程序中是已经否存在之前的电话号码
					if (!hasVehicleId(telePhone)) {
						// 没有则加入
						repeatAlarm.put(telePhone, System.currentTimeMillis());
						// log.info("收到来自" + carInfo.getString("car_no") + "的"
						// + alarm.getString("code") + "\n将联系电话"
						// + telePhone + "放入缓存");
						// 增加json消息 id=xxx,tel=xxx
						alarmSignal = JSONObject.fromObject(alarm);
						alarmSignal.put("telePhone", telePhone);
						alarmSignal.put("car_no", carInfo.getString("car_no"));
					}
					// 之前还在，报重复报警
					else {
						alarm.put("msg", "报重复报警！");
						sendFailInfo(alarm, true);
						// log.info("-----------报重复报警： " + telePhone + alarm
						// + "----------------------");
					}
				}
				// 电话号码匹配失败，发送1到服务器
				else {
					alarm.put("msg", "手机号码" + telePhone + "识别失败");
					sendFailInfo(alarm, true);
					// log.info("手机号码" + telePhone + "识别失败！");
				}
			} else {
				// log.info("目前没有数据需要处理");
			}
		} catch (Exception e) {
			log.error(GETPATH + " 服务器接口错误！" + e.getMessage(), e);
			if (!alarm.isEmpty()) {
				// 程序特殊异常，发送1到服务器
				alarm.put("msg", "(漏网之鱼)清除未缓存的数据");
				sendFailInfo(alarm, true);
				// log.info("(漏网之鱼)清除未缓存的数据" + alarm);
			}
		}
	}

	private String isTelephone(String telePhone) {
		JSONObject jsonObject = PhoneAdresss.calcMobileCity(telePhone);
		if (jsonObject != null) {
			// log.info("外地内地判读" + jsonObject);
			if (jsonObject.getString("province").equals("广东")
					&& jsonObject.getString("cityname").equals("江门")) {
				// 本地电话什么都不作
			} else {
				// 外地电话
				telePhone = "0" + telePhone;
				// log.info("外地电话" + telePhone);
			}
		} else {
			log.info("PhoneAdresss.getMobileInfo 返回为空");
		}
		return telePhone;
	}

	/**
	 * 存在车辆id这一项，已经在报警通话中了 清除过期历史数据(板卡内的缓存数据处理)
	 * 
	 * @param car_object_id
	 *            车辆的car_object_id号码
	 * @return 没有拨打：false，通话中：true
	 */
	public static boolean hasVehicleId(String telephone) {
		Iterator<String> keys = repeatAlarm.keySet().iterator();
		while (keys.hasNext()) {
			String key = keys.next();
			if ((System.currentTimeMillis() - repeatAlarm.get(key)) / 1000 > ConfigDatas
					.getTimeOut()) {
				// repeatAlarm.remove(key); ConcurrentModificationException
				keys.remove();
				// log.info("由于超时 ConfigDatas 的TimeOut下，清除了系统的电话信息"+ key);
			}
		}
		if (repeatAlarm.containsKey(telephone)) {
			return true;
		}
		// log.info("------------------根本就没有电话" + telephone);
		return false;
	}

	/**
	 * 拨打电话时间过长，没有理由啊
	 * 
	 * @param currPort
	 *            当前端口
	 * @return 超时为True,正常为False
	 */
	public static boolean isOverdued(String telephone) {
		if (telephone != null && repeatAlarm.get(telephone) != null) {
			long seconds = ((System.currentTimeMillis() - repeatAlarm
					.get(telephone)) / 1000);
			if (seconds > ConfigDatas.getPerTeletimeOut()) {
				// log.error("拨打电话超时179");
				// log.info("--------------------  拨打电话超时  " + telephone
				// + "  数据被清除-------------------");
				return true;
			}
		} else {
			// log.info("电话号码" + telephone + "为空 183");
		}
		return false;
	}

	/**
	 * 发送用户按键，清除缓冲区数据
	 * 
	 * @param userInfo
	 * @param value
	 *            如果用户已经确认误报，则为true，否则为false
	 */
	@SuppressWarnings("deprecation")
	public void sendUserPress(int currPort, boolean value) {
		JSONObject userInfo = AutoAlarmProc.vehicleId.get(currPort);
		if (userInfo != null && !userInfo.containsKey("AutoVisit")) {
			// 发送用户确认信息
			String parameter = "mode=MODE&id=" + userInfo.getString("id");
			// 日志信息
			String logString;
			if (value) {
				parameter = parameter.replace("MODE", "2");
				logString = currPort + "号端口用户取消了车辆报警(误报)，类型："
						+ userInfo.getString("code");
			} else {
				parameter = parameter.replace("MODE", "1");
				// 添加计时器，控制上传数据频率
				logString = currPort + "号端口转人工台，类型："
						+ userInfo.getString("code");
			}
			// 设置端口为空，拉底时间为0说明有一个好使(只是一个)
			try {
				HttpServlet.sendGet(PUTPATH, parameter, null);
			} catch (Exception e) {
				// log.info("发送代码失败，请求人工手动发送以下内容：");
				log.error(PUTPATH + "?" + parameter);
			}

			Log_alarm log_alarm = new Log_alarm();
			// 日志信息
			log_alarm.setAlarm_message(logString);
			// 车牌号码
			log_alarm.setAlarm_type(userInfo.getString("car_no"));
			// 日志日期
			log_alarm.setAlarmlog_date(new Date().toLocaleString());
			// 电话号码
			log_alarm.setTel_event(userInfo.getString("telePhone"));

			try {
				// java bean get 提交
				HttpServlet.sendGet(
						ConfigDatas.getLocalHost() + "SaveAlarms",
						"alarm_message="
								+ URLEncoder.encode(
										log_alarm.getAlarm_message(), "UTF-8")
								+ "&alarm_type="
								+ URLEncoder.encode(log_alarm.getAlarm_type(),
										"UTF-8")
								+ "&tel_event="
								+ URLEncoder.encode(log_alarm.getTel_event(),
										"UTF-8")
								+ "&alarmlog_date="
								+ log_alarm.getAlarmlog_date().replace(" ",
										"%20"), null);
			} catch (UnsupportedEncodingException e) {
				log.info("程序编码异常", e);
			}
		} else {
			// log.info("----------------------------------vehicleId 没有当前端口");
		}
	}

	/**
	 * 报警截获异常，没有获得正确的电话号码，语音机没有获得到可用数据 正常获取到可使用
	 * {@link #sendUserPress(int, boolean)}
	 * 
	 * @param userInfo
	 *            接口的Json消息
	 * @param retry
	 *            是否从置接收器
	 */
	public void sendFailInfo(JSONObject userInfo, boolean retry) {
		if (userInfo != null && !userInfo.containsKey("AutoVisit")) {

			String parameter = "mode=1&id=" + userInfo.getString("id");
			// 添加计时器，控制上传数据频率
			// 设置端口为空，拉底时间为0说明有一个好使(只是一个)
			try {
				HttpServlet.sendGet(PUTPATH, parameter, null);
				log.info(userInfo.getString("car_no") + " 报警类型为："
						+ userInfo.getString("code") + "  已转人工台" + "\n MSG: "
						+ userInfo.getString("msg"));
			} catch (Exception e) {
				// log.info("发送代码失败，请求人工手动发送以下内容：");
				log.error(PUTPATH + "?" + parameter);
			}
			userInfo = null;
		}
		// 继续报警截取
		if (retry) {
			currSecond = -1;
		}
		// 跳过当前位置，下一个位置循环
		else {
			currSecond = 1;
		}

	}

	/**
	 * 发送系统操作日志信息等待数据插入操作
	 * 
	 * @param paperResult
	 *            日志信息
	 */
	@SuppressWarnings("deprecation")
	public void sendVisitInfo(PaperResult paperResult) {
		if (paperResult != null) {
			List<QuestionResult> question = paperResult.getQuestionResults();
			for (int i = 0; i < question.size(); i++) {
				Log_visit log_visit = new Log_visit();
				log_visit.setQuestion_id(question.get(i).getQuestionId());
				log_visit.setVisitlog_date(paperResult.getStartDate()
						.toLocaleString());
				log_visit.setVisit_message(paperResult.getUserName());
				log_visit.setTel_event(paperResult.getUserPhone());
				try {
					log_visit.setDTMF(Integer.parseInt(question.get(i)
							.getKeyVal()));
				} catch (Exception e) {
					// System.out.println("这个按键不是数字，请查看一下");
				}
				// 如果数据获取失败则返回数据异常
				// java bean get 提交
				try {
					HttpServlet.sendGet(
							ConfigDatas.getLocalHost() + "SaveVisit",
							"visitlog_date="
									+ log_visit.getVisitlog_date().replace(" ",
											"%20")
									+ "&visit_message="
									+ URLEncoder.encode(
											log_visit.getVisit_message(),
											"UTF-8")
									+ "&tel_event="
									+ URLEncoder.encode(
											log_visit.getTel_event(), "UTF-8")
									+ "&question_id="
									+ log_visit.getQuestion_id() + "&DTMF="
									+ log_visit.getDTMF(), null);
				} catch (UnsupportedEncodingException e) {
					log.info("程序编码异常", e);
				}
			}
			log.info("WriteVisitLog返回结果为：" + paperResult);
			paperResult = null;
		}
	}

	/**
	 * 同步函数一枚
	 * 
	 * @param currPort
	 *            当前端口号
	 * @return 电话号码
	 */
	public synchronized static String getAlarmData(int currPort) {
		String result = null;
		if (alarmSignal != null && alarmSignal.containsKey("telePhone")) {
			result = alarmSignal.getString("telePhone");
			AutoAlarmProc.vehicleId.put(currPort, alarmSignal);
			// 唯一清除处，拿走数据后
			alarmSignal = null;
		}
		// 删除一条数据
		return result;
	}

	/**
	 * 清除缓冲区堆数据
	 */

	public void clearAlarm() {
		if (alarmSignal != null && !alarmSignal.containsKey("AutoVisit")) {
			alarmSignal.put("msg", "清除缓冲区堆数据");
			sendFailInfo(alarmSignal, true);
			alarmSignal = null;
		}
	}

	/**
	 * 当前已连接线路的端口 注：USB掉电之后，其保持之前不参与修改(不能同步)
	 * 
	 * @return 可用端口
	 */
	public int availablePort() {
		int result = 0;
		// 默认为可用端口
		for (int i = 0; i < MYAPI.SsmGetMaxCh(); i++) {
			if (MYAPI.SsmGetLineVoltage(i) >= 30) {
				result++;
			}
		}
		// log.info("可用端口总数为：" + result);
		return result;
	}

	/**
	 * 当currSecond == 0 可以获取一条报警(如果报警不可用，则设置currSecond =-1) 当currSecond == 2
	 * 可以获取一条用户回访电话 如果当前没有端口存在，则会让currSecond =-1
	 */

	@Override
	public void run() {
		// 定义开启客户回访
		boolean beginVisit = false;
		while (!this.isThreadStop) {
			// 延时0秒0 = 人数×人工单条数据处理时间
			SHT_PortTread.sleep(1000);
			// 延时结束
			// log.info("当前CurrSecond是" + currSecond);
			if (currSecond > ConfigDatas.getPerSecond()
					/ ConfigDatas.getPeopleSum()) {
				currSecond = -1;
			}
			// 正常数据交流
			if (currSecond == 0) {
				getHttpData();
			}
			if (currSecond == 1) {
				// 获开始一次客户回访
				beginVisit = true;
				// System.out.println("Beging A Visiter From Telephone！");
			}
			// 节日紧急报警设置 ，只进行劫警
			if ((availablePort() > 0)) {
				// 拨打客服电话，有空位置时候
				if (!ConfigDatas.isAlarmOnly())
					if (AutoAlarmProc.startTime != null
							&& AutoAlarmProc.startTime.compareTo(new Date()) < 0) {
						if (!AutoAlarmProc.getTelephoneList().isEmpty()) {
							if (beginVisit) {
								if (inPhase(new Date())) {
									if (alarmSignal == null) {
										// 手动构造客服电话类型 Telephone ,只有电话和
										alarmSignal = new JSONObject();
										alarmSignal.put("AutoVisit",
												"AutoVisit");
										// 添加手机号码本地外地判断
										String telePhone = AutoAlarmProc
												.getTelephoneList().get(0)
												.split("/")[0];
										telePhone = "9"
												+ isTelephone(telePhone);
										alarmSignal.put("telePhone", telePhone);
										alarmSignal.put("userName",
												AutoAlarmProc
														.getTelephoneList()
														.get(0).split("/")[1]);
										log.info("alarmSignal 获得了  "
												+ alarmSignal);
										AutoAlarmProc.removeTelephoneList();
										beginVisit = false;

									} else {
										// log.info("端口还被语音接警占用着呢！");
									}
								} else {
									// log.info("当前时间不能拨打语音回访，客户休息时间");
								}
							} else {
								// log.info("还没有轮到语音回访系统呢");
							}
						} else {
							AutoAlarmProc.startTime = null;
							// log.info("没有电话列表上传，目前还！");
						}
					} else {
						// log.info("还没有到需要语音的时间");
					}
				else {
					// log.info("当前已被设置为节日特殊时期，暂停自动回访，当前只进行自动接警任务！");
				}
			} else {
				// availablePort() < 1 并且 alarmSignal不为空
				if ((availablePort() < 1) && alarmSignal != null)
					// 无端口，不装箱，既不获取报警也不拨打语音回访信息
					alarmSignal.put("msg", "请检查设备，已经饱和了同志!一条电话线都没有");
				sendFailInfo(alarmSignal, false);
				// log.error("请检查设备，已经饱和了同志!一条电话线都没有，还接个什么劲警啊!?");
			}
			// 时间参数加一
			currSecond++;
		}// end while
	}

	/**
	 * 判断date是否在配置的时间区间
	 * 
	 * @param date
	 * @return
	 */
	private boolean inPhase(Date date) {
		Calendar calendar = Calendar.getInstance();
		calendar.set(1970, 0, 1);
		for (VisitPhase visitPhase : ConfigDatas.getVisitPhases()) {
			// 只要在此区间，就可以播放
			if (visitPhase.getEarlyTime().compareTo(calendar) < 0
					&& visitPhase.getLateTime().compareTo(calendar) > 0) {
				return true;
			}
		}
		return false;
	}

	private static Logger log = LoggerFactory
			.getLogger(TransactionStream.class);
	private static final SHP_A3 MYAPI = SHP_A3.INSTANCE;
	// private static final String TELPONENUM =
	// "1300580,1300581,1300582,1300583,1300584,1300585,1300586,1300587,1300588,1300589,"
	// + "1301058,"
	// +
	// "1302580,1302581,1302582,1302583,1302584,1302585,1302586,1302587,1302588,1302589,"
	// +
	// "1304810,1304811,1304812,1304813,1304814,1304815,1304816,1304817,1304818,1304819,"
	// +
	// "1305920,1305921,1305922,1305923,1305924,1305925,1305926,1305927,1305928,1305929,"
	// +
	// "1306620,1306621,1306622,1306623,1306624,1306625,1306626,1306627,1306628,1306629,"
	// + "1306640,1306641,1306642,1306643,"
	// +
	// "1307140,1307141,1307142,1307143,1307144,1307145,1307146,1307147,1307148,1307149,"
	// // ((0|2)58|481|592|662|714)[0-9]|1058|664[0-3]
	//
	// + "1310494,1310495,"
	// + "1310695,1310696,1310697,1310698,1310699,"
	// + "1311346,"
	// +
	// "1311960,1311961,1311962,1311963,1311964,1311965,1311966,1311967,1311968,1311969,"
	// +
	// "1312620,1312621,1312622,1312623,1312624,1312625,1312626,1312627,1312628,1312629,"
	// + "1314420,1314421,1314422,1314423,1314424,"
	// + "1316850,1316851,1316852,"
	// + "1316980,1316981,1316982,1316983,1316984,"
	// +
	// "1317220,1317221,1317222,1317223,1317224,1317225,1317226,1317227,1317228,1317229,"
	// + "1318940,1318941,1318942,1318943,1318944,"
	// + "1318985,1318986,1318987,1318988,1318989,"
	// //
	// (196|262|722)[0-9]|(442|698|894)[0-4]|049[4-5]|(069|898)[5-9]|1346|685[0-2]
	// + "1321126,1321127,1321128,1321129,"
	// + "1321750,"
	// + "1322646,"
	// + "1322647,"
	// + "1322690,1322691,1322692,1322693,1322694,"
	// + "1322695,"
	// + "1322908,"
	// + "1322909,"
	// + "1322913,"
	// + "1322914,"
	// + "1323240,1323241,1323242,1323243,1323244,"
	// + "1323285,1323286,1323287,1323288,1323289,"
	// + "1323290,1323291,1323292,1323293,1323294,"
	// + "1324215,1324216,1324217,1324218,1324219,"
	// + "1324248,1324249,"
	// + "1324490,1324491,1324492,1324493,"
	// + "1324658,1324659,"
	// + "1324910,1324911,1324912,"
	// + "1325001,1325002,"
	// + "1325046,1325047,"
	// + "1325750,"
	// + "1326645,1326646,"
	// + "1326648,1326649,"
	// + "1326765,1326766,1326767,1326768,1326769,"
	// +
	// "1328610,1328611,1328612,1328613,1328614,1328615,1328616,1328617,1328618,1328619,"
	// + "1328660,1328661,1328662,1328663,"
	// //
	// 861[0-9]|(269|324|329)[0-4]|(328|421|676)[5-9]|112[6-9]|(449|866)[0-3]|491[0-2]|(424|465|664)[8-9]|504[6-7]|664[5-6]|500[1-2]|1750|26(46|47|95)|29(0[8-9]|1[3-4])|5750
	//
	// + "1330255,"
	// + "1330258,"
	// + "1330288,"
	// + "1331670,1331671,1331672,1331673,1331674,1331675,1331676,"
	// + "1331863,1331864,1331865,1331866,"
	// + "1332288,1332289,"
	// + "1332680,1332681,1332682,1332683,1332684,"
	// + "1335640,1335641,1335642,1335643,1335644,"
	// + "1335655,1335656,1335657,1335658,1335659,"
	// + "1336020,1336021,1336022,"
	// + "1336043,1336044,"
	// + "1337750,"
	// + "1338095,"
	// + "1338096,1338097,1338098,1338099,"
	// + "1339205,1339206,1339207,1339208,1339209,"
	// + "1339250,1339251,"
	// //
	// 167[0-6]|(268|564)[0-4]|(565|920)[5-9]|186[3-6]|809[6-9]|228[8-9]|604[3-4]|602[0-2]|925[0-1]|02(5(5|8)|88)|7750|8095
	// + "1341415,1341416,1341417,1341418,1341419,"
	// +
	// "1342250,1342251,1342252,1342253,1342254,1342255,1342256,1342257,1342258,1342259,"
	// +
	// "1342260,1342261,1342262,1342263,1342264,1342265,1342266,1342267,1342268,1342269,"
	// +
	// "1342270,1342271,1342272,1342273,1342274,1342275,1342276,1342277,1342278,1342279,"
	// +
	// "1342490,1342491,1342492,1342493,1342494,1342495,1342496,1342497,1342498,1342499,"
	// +
	// "1342670,1342671,1342672,1342673,1342674,1342675,1342676,1342677,1342678,1342679,"
	// +
	// "1342680,1342681,1342682,1342683,1342684,1342685,1342686,1342687,1342688,1342689,"
	// +
	// "1342710,1342711,1342712,1342713,1342714,1342715,1342716,1342717,1342718,1342719,"
	// +
	// "1342720,1342721,1342722,1342723,1342724,1342725,1342726,1342727,1342728,1342729,"
	// +
	// "1342730,1342731,1342732,1342733,1342734,1342735,1342736,1342737,1342738,1342739,"
	// +
	// "1342740,1342741,1342742,1342743,1342744,1342745,1342746,1342747,1342748,1342749,"
	// + "1342825,1342826,1342827,1342828,1342829,"
	// +
	// "1343170,1343171,1343172,1343173,1343174,1343175,1343176,1343177,1343178,1343179,"
	// +
	// "1343220,1343221,1343222,1343223,1343224,1343225,1343226,1343227,1343228,1343229,"
	// + "1343515,1343516,1343517,1343518,1343519,"
	// + "1343730,1343731,1343732,1343733,1343734,"
	// // (2(2[5-7]|49|6[7-8]|7[1-4])|3(17|22))[0-9]|(141|282|351)[5-9]|373[0-4]
	// + "1350023,"
	// + "1350028,"
	// +
	// "1352830,1352831,1352832,1352833,1352834,1352835,1352836,1352837,1352838,1352839,"
	// +
	// "1353470,1353471,1353472,1353473,1353474,1353475,1353476,1353477,1353478,1353479,"
	// + "1353480,1353481,1353482,1353483,1353484,"
	// +
	// "1353600,1353601,1353602,1353603,1353604,1353605,1353606,1353607,1353608,1353609,"
	// +
	// "1353610,1353611,1353612,1353613,1353614,1353615,1353616,1353617,1353618,1353619,"
	// + "1353620,1353621,1353622,1353623,1353624,"
	// +
	// "1354210,1354211,1354212,1354213,1354214,1354215,1354216,1354217,1354218,1354219,"
	// + "1354495,1354496,1354497,1354498,1354499,"
	// +
	// "1355560,1355561,1355562,1355563,1355564,1355565,1355566,1355567,1355568,1355569,"
	// +
	// "1355690,1355691,1355692,1355693,1355694,1355695,1355696,1355697,1355698,1355699,"
	// // (283|347|36[0-1]|421|556|569)[0-9]|(348|362)[0-4]|449[5-9]|002(3|8)
	// + "1360035,"
	// + "1361225,1361226,1361227,1361228,1361229,"
	// + "1362017,1362018,1362019,"
	// + "1362256,1362257,"
	// +
	// "1363040,1363041,1363042,1363043,1363044,1363045,1363046,1363047,1363048,1363049,"
	// +
	// "1363180,1363181,1363182,1363183,1363184,1363185,1363186,1363187,1363188,1363189,"
	// + "1363205,1363206,1363207,1363208,1363209,"
	// + "1365270,1365271,1365272,1365273,1365274,"
	// + "1366490,1366491,1366492,1366493,1366494,"
	// +
	// "1367280,1367281,1367282,1367283,1367284,1367285,1367286,1367287,1367288,1367289,"
	// +
	// "1367290,1367291,1367292,1367293,1367294,1367295,1367296,1367297,1367298,1367299,"
	// + "1367615,1367616,1367617,1367618,1367619,"
	// +
	// "1368040,1368041,1368042,1368043,1368044,1368045,1368046,1368047,1368048,1368049,"
	// + "1368050,1368051,"
	// +
	// "1368690,1368691,1368692,1368693,1368694,1368695,1368696,1368697,1368698,1368699,"
	// //
	// (304|318|72[8-9]|804|869)[0-9]|(527|649)[0-4]|(122|320|761)[5-9]|201[7-9]|225[6-7]|805[0-1]|0035
	// + "1370220,1370221,1370222,1370223,1370224,"
	// + "1370227,1370228,1370229,"
	// + "1370240,1370241,"
	// + "1370258,1370259,"
	// + "1370270,1370271,"
	// + "1370284,"
	// + "1370309,"
	// + "1370961,"
	// + "1371725,1371726,1371727,1371728,1371729,"
	// +
	// "1372590,1372591,1372592,1372593,1372594,1372595,1372596,1372597,1372598,1372599,"
	// + "1372615,1372616,1372617,1372618,1372619,"
	// +
	// "1375030,1375031,1375032,1375033,1375034,1375035,1375036,1375037,1375038,1375039,"
	// + "1376050,1376051,1376052,1376053,1376054,"
	// +
	// "1379420,1379421,1379422,1379423,1379424,1379425,1379426,1379427,1379428,1379429,"
	// //
	// (259|503|942)[0-9]|(022|605)[0-4]|(172|261)[5-9]|022[7-9]|025[8-9]|(024|027)[0-1]|0(284|309|961)
	// + "1380260,1380261,"
	// + "1380960,"
	// +
	// "1382230,1382231,1382232,1382233,1382234,1382235,1382236,1382237,1382238,1382239,"
	// +
	// "1382240,1382241,1382242,1382243,1382244,1382245,1382246,1382247,1382248,1382249,"
	// +
	// "1382400,1382401,1382402,1382403,1382404,1382405,1382406,1382407,1382408,1382409,"
	// +
	// "1382700,1382701,1382702,1382703,1382704,1382705,1382706,1382707,1382708,1382709,"
	// +
	// "1382800,1382801,1382802,1382803,1382804,1382805,1382806,1382807,1382808,1382809,"
	// // (2(2[3-4]|(4|7|8)0)[0-9]|0(26[0-1]|960)
	// + "1390255,"
	// + "1390258,"
	// + "1390288,"
	// + "1392305,1392306,1392307,1392308,"
	// + "1392468,"
	// +
	// "1392900,1392901,1392902,1392903,1392904,1392905,1392906,1392907,1392908,1392909,"
	// + // 290[0-9]|230[5-8]|02(5(5|8)|88)|2468
	// "1452940,1452941,1452942,"
	// + "1471468,"
	// + "1471499,"
	// + "1471530,"
	// + "1471555,1471573,1471585,1471586,"
	// // 5294[0-2]|7(14(68|99)|15(30|55|73|8[5-6]))
	// + "1500750,"
	// + "1501433,1501434,"
	// + "1501501,1501502,1501503,1501504,1501505,"
	// + "1501884,1501889,"
	// + "1501983,1501984,1501985,1501986,1501987,1501988,1501989,"
	// + "1508811,1508812,1508813,"
	// + "1508980,1508981,1508982,1508983,1508984,"
	// // 0750|143[3-4]|150[1-5]|188(4|9)|198[3-9]|881[1-3]|898[0-4]
	// + "1510750,"
	// + "1511340,"
	// + "1511395,1511396,"
	// + "1511885,"
	// +
	// // (075|134)0|139[5-6]|1885
	// "1520750,"
	// + "1521698,1521699,"
	// + "1521900,1521901,"
	// + "1521955,1521956,"
	// + "1521968,1521969,"
	// + "1522075,1522076,1522077,1522078,1522079,"
	// // 0750|1(69[8-9]|9(0[0-1]|5[5-6]|6[8-9]))|207[5-9]
	// + "1530750,"
	// + "1532210,1532211,1532212,"
	// + "1532264,1532265,1532266,1532267,1532268,1532269,"
	// + "1533810,1533811,1533812,1533813,1533814,"
	// +
	// "1536220,1536221,1536222,1536223,1536224,1536225,1536226,1536227,1536228,1536229,"
	// + "1536250,1536251,1536252,1536253,1536254,"
	// // 622[0-9]|0750|22(1[0-2]|6[4-9])|(381|625)[0-4]
	// + "1560255,"
	// + "1560258,"
	// + "1560288,"
	// + "1566010,1566011,1566012,1566013,1566014,1566015,1566016,"
	// + "1569750,"
	// // 02(5(5|8)|88)|601[0-6]|9750
	// + "1571821,"
	// + "1572412,"
	// + "1572830,"
	// + "1572888,"
	// // 1821|2(412|8(30|88))
	// + "1580750,"
	// + "1581375,1581376,1581377,1581378,1581379,"
	// + "1581573,1581574,1581575,1581576,1581577,1581578,1581579,"
	// + "1581590,1581591,"
	// +
	// "1581970,1581971,1581972,1581973,1581974,1581975,1581976,1581977,1581978,1581979,"
	// + "1581990,1581991,1581992,1581993,1581994,"
	// +
	// "1587500,1587501,1587502,1587503,1587504,1587505,1587506,1587507,1587508,1587509,"
	// + "1587625,1587626,1587627,1587628,1587629,"
	// + "1589970,1589971,1589972,1589973,1589974,"
	// // (197|750)[0-9]|(199|997)[0-4]|(137|762)[5-9]|15(9[0-1]|7[3-9])|0750
	// + "1590750,"
	// +
	// "1591360,1591361,1591362,1591363,1591364,1591365,1591366,1591367,1591368,1591369,"
	// + "1591730,1591731,1591732,1591733,1591734,"
	// +
	// "1591780,1591781,1591782,1591783,1591784,1591785,1591786,1591787,1591788,1591789,"
	// +
	// "1597500,1597501,1597502,1597503,1597504,1597505,1597506,1597507,1597508,1597509,"
	// +
	// "1597640,1597641,1597642,1597643,1597644,1597645,1597646,1597647,1597648,1597649,"
	// +
	// "1599210,1599211,1599212,1599213,1599214,1599215,1599216,1599217,1599218,1599219,"
	// + "1599485,1599486,1599487,1599488,1599489,"
	// // (136|178|750|764|921)[0-9]|173[0-4]|948[5-9]|0750
	// + "1820750,"
	// + "1821911,"
	// // 0750|1911
	// + "1860750,"
	// + "1866663,"
	// +
	// "1867500,1867501,1867502,1867503,1867504,1867505,1867506,1867507,1867508,1867509,"
	// + "1867592,1867593,"
	// + "1868850,1868852,1868855,1868856,"
	// + "1868860,"
	// // 750[0-9]|0750|6663|759[2-3]|885(0|2|5|6)|8860
	// + "1880255,"
	// + "1880258,"
	// + "1880750,"
	// + "1882305,1882306,1882307,1882308,"
	// +
	// "1882400,1882401,1882402,1882403,1882404,1882405,1882406,1882407,1882408,1882409,"
	// + "1882534,1882535,"
	// + "1882599,"
	// + "1882615,1882616,1882617,1882618,1882619,"
	// // 240[0-9]|261[5-9]|230[5-8]|253[4-5]|0(25(5|8)|750)|2599
	// + "1890255,"
	// + "1890258,"
	// + "1890288,"
	// + "1892200,1892201,1892202,1892203,1892204,1892205,"
	// + "1892305,1892306,1892307,1892308,"
	// + "1892335,"
	// + "1892468,"
	// +
	// "1892900,1892901,1892902,1892903,1892904,1892905,1892906,1892907,1892908,1892909,"
	// + "1893309,"
	// + "1894869,"
	// + "1894895,1894896,1894897,"
	// + "1899814,1899815," + "1899853";
}