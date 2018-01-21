package com.wechatpayment;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.UUID;
import java.util.Random;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import com.alibaba.druid.support.logging.Log;
import com.alibaba.druid.support.logging.LogFactory;
import com.infomings.shop.base.ControllerBase;
import com.infomings.shop.dao.ShopcartMapper;
import com.infomings.shop.entity.Item;
import com.infomings.shop.entity.Orders;
import com.infomings.shop.entity.Shopcart;
import com.infomings.shop.entity.Stock;
import com.infomings.shop.entity.Store;
import com.infomings.shop.entity.User;
import com.infomings.shop.entity.WechatInfo;
import com.infomings.shop.service.ItemService;
import com.infomings.shop.service.OrdersService;
import com.infomings.shop.service.ShopcartService;
import com.infomings.shop.service.StockService;
import com.infomings.shop.service.StoreService;
import com.infomings.shop.service.UserService;
import com.infomings.shop.utils.TimeUtil;
import com.infomings.shop.utils.WXPayUtil;
import com.infomings.shop.utils.WechatConstants;
import com.jfinal.kit.HttpKit;
import com.lly835.bestpay.enums.BestPayTypeEnum;
import com.lly835.bestpay.model.PayRequest;
import com.lly835.bestpay.model.PayResponse;
import com.lly835.bestpay.service.impl.BestPayServiceImpl;
import com.lly835.bestpay.utils.JsonUtil;
import com.sun.corba.se.impl.oa.toa.TOA;
import com.sun.xml.internal.bind.v2.TODO;



/**
 * @author LuoGuang 
 * @Description: 订单控制层
 * @version 创建时间：2018年1月6日 上午10:45:17
 */
public class OrdersController{


	/**
	 * @author: LuoGuang
	 * @Description: 微信统一下单
	 * @param request
	 * @param response
	 * @param model
	 * @param orderNumber 订单编号
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "paymentWechat")
	public String paymentWechat(HttpServletRequest request, HttpServletResponse response, Model model,
			@RequestParam("orderNumber") String orderNumber) throws Exception {


		// 总价格
		Double numbers = 0.00;
		// 商品名称
		String itembody = "";

		String openid = "";

		// 商品信息
		String body = itembody;

		Map<String, String> map = new HashMap<String, String>();

		// 支付物品的订单编号
		String out_trade_no = "aaaa";
		model.addAttribute("orderNumber", out_trade_no);

		// 支付金额。微信识别以分为单位
		String money = numbers * 100 + "";
		String[] smill = money.split("\\.");
		String total_fee = smill[0];

		// 微信的appid
		String appid = WechatConstants.APPID;

		// 商户号
		String mch_id = WechatConstants.POS_NUMBER;

		// 密匙,商户平台的支付API密匙，注意是商户平台，不是微信平台
		String key = WechatConstants.POS_SECRET;

		map.put("appid", appid);
		map.put("mch_id", mch_id);
		map.put("nonce_str", UUID.randomUUID().toString().replace("-", ""));
		map.put("body", body);
		map.put("out_trade_no", out_trade_no);
		map.put("total_fee", total_fee);

		// 这里是支付成功后返回的地址，微信会以XML形式放回数据。
		map.put("notify_url", "http://shop.infomings.com/order/paySuccessAjax.htm");
		map.put("trade_type", "JSAPI");
		map.put("openid", openid);

		// 这里传入Map集合和key商户支付密匙
		String paySign = WXPayUtil.generateSignature(map, key);
		map.put("sign", paySign);

		// 将map转为XML格式
		String xml = WXPayUtil.mapToXml(map);

		// 统一下单
		String url = "https://api.mch.weixin.qq.com/pay/unifiedorder";
		String xmlStr = HttpKit.post(url, xml);

		// prepayid由微信返回的 。
		String prepayid = "";

		log.info("xmlStr（" + xmlStr + "）");
		if (xmlStr.indexOf("SUCCESS") != -1) {
			Map<String, String> map2 = WXPayUtil.xmlToMap(xmlStr);
			prepayid = (String) map2.get("prepay_id");
		}

		Map<String, String> packageParams = new HashMap<String, String>();

		// 获得时间搓
		String curs = System.currentTimeMillis() / 1000 + "";

		// 获取随机串
		String nonceStr = System.currentTimeMillis() + "";

		// 微信签名
		packageParams.put("appId", appid);
		packageParams.put("timeStamp", curs);
		packageParams.put("nonceStr", nonceStr);
		packageParams.put("package", "prepay_id=" + prepayid);
		packageParams.put("signType", "MD5");
		String packageSign = WXPayUtil.generateSignature(packageParams, key);
		packageParams.put("paySign", packageSign);

		// 保存变量
		model.addAttribute("appId", appid);
		model.addAttribute("paytimestamp", curs);
		model.addAttribute("paynonceStr", nonceStr);
		model.addAttribute("paypackage", "prepay_id=" + prepayid);
		model.addAttribute("paysignType", "MD5");
		model.addAttribute("paySign", packageSign);
		return "order/paycreate";
	}

	/**
	 * @author: LuoGuang
	 * @Description: 微信支付异步返回
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value = "paySuccessAjax")
	@ResponseBody
	public String paySuccessAjax(HttpServletRequest request, HttpServletResponse response) throws Exception {

		log.info("paySuccess: begin\n");
		try {
			ServletInputStream in = request.getInputStream();
			StringBuffer buff = new StringBuffer();
			try {
				byte[] b = new byte[4096];
				for (int length; (length = in.read(b)) != -1;) {
					buff.append(new String(b, 0, length));
				}
			} catch (IOException e) {
				log.info("streamToString : === " + e.getMessage());
				buff = buff.delete(0, buff.length());
				e.printStackTrace();
			}
			String result = buff.toString();

			log.info("result：== " + result);
			log.info("xStreamUtil begin...");

			if (result != null && !result.equals("")) {
				Map<String, String> map = WXPayUtil.xmlToMap(result);
				for (Object keyValue : map.keySet()) {
					log.info(keyValue + "=" + map.get(keyValue));
					
				}
				log.info("result_code:" + map.get("result_code").equalsIgnoreCase("SUCCESS"));
				if (map.get("result_code").equalsIgnoreCase("SUCCESS")) {
//					 String sign = CommonPayment.SuccessSign(map, CommonWeChat.MCH_KEY);
//					 log.info("215 sign=" + map.get("sign") + " APP_PAYKRY=" + sign);
//					 if (sign != null && map.get("sign").equals(sign)) {
					//String sign = CommonPayment.SuccessSign(map, CommonWeChat.MCH_KEY);
					boolean isSignatureValid = WXPayUtil.isSignatureValid(map, map.get("sign"));
					log.info("isSignatureValid("+isSignatureValid+")");
					
					//订单编号
					String out_trade_no = map.get("out_trade_no");
					
					// 根据订单编号获得全部订单
					Orders orders = new Orders();
					orders.setOrderNumber(out_trade_no);
					List<Orders> orderslist = ordersService.findList(orders);
					
					if(orderslist.size() > 0 && orderslist.get(0).getOrderStatus() == 1) {
						
						// 获得订单价钱
						Double numbers = 0.00;
						for (Orders ord : orderslist) {
							numbers += ord.getOrderMoney();
						}
						
						String money = numbers * 100 + "";
						String[] smill = money.split("\\.");
						String oldtotal_fee = smill[0];
						
						User user = userService.get(map.get("openid"));
						
						//if(user != null) {
							//金额
							String total_fee = map.get("total_fee");
							
							//处理订单
							if(oldtotal_fee.equals(total_fee) && map.get("appid").equals(WechatConstants.APPID)) {
								log.info("支付成功修改订单状态");
								ordersService.updateOrderSuccess(out_trade_no);
								return "<xml><return_code><![CDATA[SUCCESS]]></return_code></xml>";
							}
						//}
					}
					
				}
			}
			return "<xml><return_code><![CDATA[FAIL]]></return_code></xml>";
		} catch (Exception ex) {
			log.info("paySuccess Exception = " + ex.getMessage());
			ex.printStackTrace();
		}
		log.info("paySuccess  === end\n");
		return null;

	}
	
	/**
	 * @author: LuoGuang
	 * @Description: 支付成功 （不做订单状态修改）
	 * @return
	 */
	@RequestMapping(value = "paySuccess")
	public String paySuccess(@RequestParam("orderNumber") String orderNumber,Model model) {
		model.addAttribute("orderNumber", orderNumber);
		return "order/payment-success";
	}
	

	/**
	 * @author: LuoGuang
	 * @Description: 支付失败 
	 * @return
	 */
	@RequestMapping(value = "payFailure")
	public String payFailure() {
		return "order/payment-failure";
	}
}
