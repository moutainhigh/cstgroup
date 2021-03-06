package com.drag.cstgroup.scoremall.service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.drag.cstgroup.common.Constant;
import com.drag.cstgroup.common.exception.AMPException;
import com.drag.cstgroup.keruyun.service.KeruyunService;
import com.drag.cstgroup.scoremall.dao.OrderDetailDao;
import com.drag.cstgroup.scoremall.dao.OrderInfoDao;
import com.drag.cstgroup.scoremall.dao.OrderShipperDao;
import com.drag.cstgroup.scoremall.dao.ProductInfoDao;
import com.drag.cstgroup.scoremall.entity.OrderDetail;
import com.drag.cstgroup.scoremall.entity.OrderInfo;
import com.drag.cstgroup.scoremall.entity.OrderShipper;
import com.drag.cstgroup.scoremall.entity.ProductInfo;
import com.drag.cstgroup.scoremall.form.OrderDetailForm;
import com.drag.cstgroup.scoremall.form.OrderInfoForm;
import com.drag.cstgroup.scoremall.resp.OrderResp;
import com.drag.cstgroup.scoremall.vo.OrderDetailVo;
import com.drag.cstgroup.scoremall.vo.OrderInfoVo;
import com.drag.cstgroup.user.dao.UserDao;
import com.drag.cstgroup.user.dao.UserScoreUsedRecordDao;
import com.drag.cstgroup.user.dao.UserTicketDao;
import com.drag.cstgroup.user.dao.UserTicketTemplateDao;
import com.drag.cstgroup.user.entity.User;
import com.drag.cstgroup.user.entity.UserScoreUsedRecord;
import com.drag.cstgroup.user.entity.UserTicket;
import com.drag.cstgroup.user.entity.UserTicketTemplate;
import com.drag.cstgroup.user.resp.ScoreResp;
import com.drag.cstgroup.user.service.ScoreService;
import com.drag.cstgroup.utils.BeanUtils;
import com.drag.cstgroup.utils.DateUtil;
import com.drag.cstgroup.utils.StringUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OrderService {

	@Autowired
	private OrderInfoDao orderInfoDao;
	@Autowired
	private OrderDetailDao orderDetailDao;
	@Autowired
	private OrderShipperDao orderShipperDao;
	@Autowired
	private UserDao userDao;
	@Autowired
	private ProductInfoDao productInfoDao;
	@Autowired
	private UserScoreUsedRecordDao userScoreUsedRecordDao;
	@Autowired
	private ScoreService scoreService;
	@Autowired
	private KeruyunService keruyunService;
	@Autowired
	private UserTicketDao userTicketDao;
	@Autowired
	private UserTicketTemplateDao userTicketTemplateDao;
	
	/**
	 * 有机食品购买下单
	 * @param form
	 * @return
	 */
	@Transactional
	public OrderResp purchase(OrderInfoForm form) {
		log.info("【有机类商品下单传入参数】:{}",JSON.toJSONString(form));
		OrderResp resp = new OrderResp();
		try {
			String orderid = StringUtil.uuid();
			int goodsId = form.getGoodsId();
			String goodsName = form.getGoodsName();
			String type = form.getType();
			//购买总数量
			int number = form.getNumber();
			//消耗总积分
			int score = form.getScore();
			String openid = form.getOpenid();
			String buyName = form.getBuyName();
			String phone = form.getPhone();
			//订单方式（0:快递到家，1:送货上门）
			int orderType = form.getOrderType();
			//收货人
			String receiptName = form.getReceiptName();
			//收货人联系方式
			String receiptTel = form.getReceiptTel();
			//所在区域
			String region = form.getRegion();
			//邮政编码
			String postalcode = form.getPostalcode();
			//地址
			String receiptAddress  = form.getReceiptAddress();
			User user = userDao.findByOpenid(openid);
			ProductInfo goods = productInfoDao.findGoodsDetail(goodsId);
			if(user != null) {
				int uid = user.getId();
				//验证参数
				resp = this.checkParam(user,goods,form);
				String returnCode = resp.getReturnCode();
				if(!returnCode.equals(Constant.SUCCESS)) {
					return resp;
				}
				//插入订单表
				OrderInfo order = new OrderInfo();
				order.setId(order.getId());
				order.setOrderid(orderid);
				order.setGoodsId(goodsId);
				order.setGoodsName(goodsName + "等商品");
				order.setGoodsImg(goods.getGoodsImgs());
				order.setType(type);
				order.setNumber(number);
				order.setScore(score);
				order.setOrderstatus(OrderInfo.ORDERSTATUS_SUCCESS);
				order.setUid(uid);
				order.setBuyName(buyName);
				order.setPhone(phone);
				order.setOrderType(orderType);
				order.setDeliverystatus(OrderInfo.STATUS_UNDELIVERY);
				order.setReceiptName(receiptName);
				order.setReceiptTel(receiptTel);
				order.setRegion(region);
				order.setPostalcode(postalcode);
				order.setReceiptAddress(receiptAddress);
				order.setIsBilling(OrderInfo.ISBILLING_NO);
				order.setCreateTime(new Timestamp(System.currentTimeMillis()));
				order.setUpdateTime(new Timestamp(System.currentTimeMillis()));
				orderInfoDao.save(order);
				
				//插入物流表
				OrderShipper shipper = new OrderShipper();
				shipper.setId(shipper.getId());
				shipper.setOrderid(orderid);
				shipper.setUid(uid);
				shipper.setReceiptName(receiptName);
				shipper.setReceiptTel(receiptTel);
				shipper.setReceiptAddress(receiptAddress);
				shipper.setCreateTime(new Timestamp(System.currentTimeMillis()));
				orderShipperDao.save(shipper);
				
				List<OrderDetailForm> orderList = form.getOrderDetail();
				if(orderList != null && orderList.size() > 0) {
					for(OrderDetailForm detail : orderList) {
						//插入订单详情
						int dGoodsId = detail.getGoodsId();
						String dGoodsName = detail.getGoodsName();
						String dNorms = detail.getNorms();
						int dNumber = detail.getNumber();
						int dScore  = detail.getScore();
						OrderDetail orderDetail = new OrderDetail();
						orderDetail.setId(orderDetail.getId());
						orderDetail.setUid(uid);
						orderDetail.setOrderid(orderid);
						orderDetail.setGoodsId(dGoodsId);
						orderDetail.setGoodsName(dGoodsName);
						orderDetail.setNorms(dNorms);
						orderDetail.setScore(dScore);
						orderDetail.setNumber(dNumber);
						orderDetail.setType(type);
						orderDetail.setCreateTime(new Timestamp(System.currentTimeMillis()));
						orderDetail.setUpdateTime(new Timestamp(System.currentTimeMillis()));
						orderDetailDao.save(orderDetail);
					}
				}else {
					resp.setReturnCode(Constant.ORDERNOTEXISTS);
					resp.setErrorMessage("订单详情不存在，请添加商品!");
					log.error("【有机类商品下单订单参数错误】,{}",JSON.toJSONString(orderList));
					return resp;
				}
				
				//客如云扣减积分
				String customerId = user.getCustomerId();
				String remark = String.format("%s购买%s",customerId,goodsName+"等商品");
				ScoreResp mySresp = keruyunService.cutScore(customerId, score,remark);
				String mySreturnCode = mySresp.getReturnCode();
				if(!Constant.SUCCESS.equals(mySreturnCode)) {
					resp.setReturnCode(Constant.RECHARGE_ERROR);
					resp.setErrorMessage("该用户积分异常，请联系客服!");
					log.error("【用户积分扣减异常】:sresp = {}",JSON.toJSONString(mySresp));
					return resp;
				}
				String kryMyCurrentPoints = mySresp.getCurrentPoints();
				if(!StringUtil.isEmpty(kryMyCurrentPoints)) {
					user.setScore(Integer.parseInt(kryMyCurrentPoints));
				}else {
					resp.setReturnCode(Constant.RECHARGE_ERROR);
					resp.setErrorMessage("该用户积分异常，请联系客服!");
					log.error("【用户积分扣减异常】:mySresp = {}",JSON.toJSONString(mySresp));
					return resp;
				}
				
				//给上级返积分
				int parentid = user.getParentid();
				if(parentid != 0) {
					scoreService.returnScore(uid,parentid, score);
				}
				
				//新增积分使用记录
				UserScoreUsedRecord scoreUsedRecord = new UserScoreUsedRecord();
				scoreUsedRecord.setId(scoreUsedRecord.getId());
				scoreUsedRecord.setUid(uid);
				scoreUsedRecord.setGoodsId(goodsId);
				scoreUsedRecord.setGoodsName(goodsName + "等商品");
				scoreUsedRecord.setType(type);
				scoreUsedRecord.setScore(Integer.parseInt(kryMyCurrentPoints));
				scoreUsedRecord.setUsedScore(score);
				scoreUsedRecord.setCreateTime(new Timestamp(System.currentTimeMillis()));
				userScoreUsedRecordDao.save(scoreUsedRecord);
				//新增购买人数次数
				this.addSuccTimes(goods);
				
				resp.setReturnCode(Constant.SUCCESS);
				resp.setErrorMessage("下单成功!");
				
			}
		} catch (Exception e) {
			log.error("系统异常,{}",e);
			throw AMPException.getException("下单异常!");
		}
		return resp;
	}
	
	
	/**
	 * 其他不需要购物车的服务类商品购买(旅游服务，企业服务，营养套餐)
	 * 需传入的参数：goodsId：商品编号、type:类型、number:购买数量、score:消耗积分,
	 * openid,buy_name,phone,
	 * @param form
	 * @return
	 */
	@Transactional
	public OrderResp commPurchase(OrderInfoForm form) {
		log.info("【服务类商品下单传入参数】:{}",JSON.toJSONString(form));
		OrderResp resp = new OrderResp();
		try {
			String orderid = StringUtil.uuid();
			int goodsId = form.getGoodsId();
			String goodsName = form.getGoodsName();
			String type = form.getType();
			//营养套餐使用
			String norms = form.getNorms();
			//购买总数量
			int number = form.getNumber();
			//消耗总积分
			int score = form.getScore();
			String openid = form.getOpenid();
			String buyName = form.getBuyName();
			String phone = form.getPhone();
			
			User user = userDao.findByOpenid(openid);
			ProductInfo goods = productInfoDao.findGoodsDetail(goodsId);
			//验证参数
			resp = this.checkParam(user,goods,form);
			String returnCode = resp.getReturnCode();
			if(!returnCode.equals(Constant.SUCCESS)) {
				return resp;
			}
			int uid = user.getId();
			//插入订单表
			OrderInfo order = new OrderInfo();
			order.setId(order.getId());
			order.setOrderid(orderid);
			order.setGoodsId(goodsId);
			order.setGoodsName(goodsName);
			order.setGoodsImg(goods.getGoodsImgs());
			order.setType(type);
			order.setNumber(number);
			order.setScore(score);
			//已付款
			order.setOrderstatus(OrderInfo.ORDERSTATUS_SUCCESS);
			order.setUid(uid);
			order.setBuyName(buyName);
			order.setPhone(phone);
			order.setNorms(norms);
			order.setIsBilling(OrderInfo.ISBILLING_NO);
			order.setCreateTime(new Timestamp(System.currentTimeMillis()));
			orderInfoDao.save(order);
//			int nowScore = user.getScore();
			
			//客如云扣减积分
			String customerId = user.getCustomerId();
			String remark = String.format("%s购买%s",customerId,goodsName);
			ScoreResp mySresp = keruyunService.cutScore(customerId, score,remark);
			String mySreturnCode = mySresp.getReturnCode();
			if(!Constant.SUCCESS.equals(mySreturnCode)) {
				resp.setReturnCode(Constant.RECHARGE_ERROR);
				resp.setErrorMessage("该用户积分异常，请联系客服!");
				log.error("【用户积分扣减异常】:sresp = {}",JSON.toJSONString(mySresp));
				return resp;
			}
			String kryMyCurrentPoints = mySresp.getCurrentPoints();
			if(!StringUtil.isEmpty(kryMyCurrentPoints)) {
				user.setScore(Integer.parseInt(kryMyCurrentPoints));
			}else {
				resp.setReturnCode(Constant.RECHARGE_ERROR);
				resp.setErrorMessage("该用户积分异常，请联系客服!");
				log.error("【用户积分扣减异常】:mySresp = {}",JSON.toJSONString(mySresp));
				return resp;
			}
			
			//给上级返积分
			int parentid = user.getParentid();
			if(parentid != 0) {
				scoreService.returnScore(uid,parentid, score);
			}
			
			//发送卡券
			if(type.equals("ly") || type.equals("tc")) {
				UserTicketTemplate  template = userTicketTemplateDao.findByGoodsIdAndType(goodsId, type);
				//发送卡券
				if(template != null) {
					for(int i = 0;i < number; i++) {
						UserTicket ticket = new UserTicket();
						BeanUtils.copyProperties(template, ticket);
						ticket.setId(ticket.getId());
						ticket.setUid(uid);
						ticket.setNumber(1);
						ticket.setNorms(norms);
						ticket.setStatus(UserTicket.STATUS_NO);
						ticket.setCreateTime((new Timestamp(System.currentTimeMillis())));
						userTicketDao.save(ticket);
					}
				}
			}
			
			//新增积分使用记录
			UserScoreUsedRecord scoreUsedRecord = new UserScoreUsedRecord();
			scoreUsedRecord.setId(scoreUsedRecord.getId());
			scoreUsedRecord.setUid(uid);
			scoreUsedRecord.setGoodsId(goodsId);
			scoreUsedRecord.setGoodsName(goodsName);
			scoreUsedRecord.setType(type);
			scoreUsedRecord.setScore(Integer.parseInt(kryMyCurrentPoints));
			scoreUsedRecord.setUsedScore(score);
			scoreUsedRecord.setCreateTime(new Timestamp(System.currentTimeMillis()));
			userScoreUsedRecordDao.save(scoreUsedRecord);
			//新增购买人数次数
			this.addSuccTimes(goods);
		} catch (Exception e) {
			log.error("系统异常,{}",e);
			throw AMPException.getException("系统异常!");
		}
		resp.setReturnCode(Constant.SUCCESS);
		resp.setErrorMessage("下单成功!");
		return resp;
	}
	
	
	@Transactional
	public OrderResp openBill(OrderInfoForm form) {
		log.info("【开发票传入参数】:{}",JSON.toJSONString(form));
		OrderResp resp = new OrderResp();
		try {
			String orderid = form.getOrderid();
			
			OrderInfo order = orderInfoDao.findByOrderId(orderid);
			if(order != null) {
				//开票类型
				String billingType = form.getBillingType();
				//公司抬头/个人抬头
				String invPayee = form.getInvPayee();
				//发票内容
				String invContent = form.getInvContent();
				
				order.setIsBilling(OrderInfo.ISBILLING_YES);
				order.setBillingType(billingType);
				order.setInvPayee(invPayee);
				order.setInvContent(invContent);
				order.setBillTime(new Timestamp(System.currentTimeMillis()));
				order.setUpdateTime(new Timestamp(System.currentTimeMillis()));
				orderInfoDao.saveAndFlush(order);
			}else {
				resp.setReturnCode(Constant.ORDERNOTEXISTS);
				resp.setErrorMessage("订单不存在!");
				log.error("【订单不存在】，goodsId={}",orderid);
				return resp;
			}
		} catch (Exception e) {
			log.error("系统异常,{}",e);
			throw AMPException.getException("系统异常!");
		}
		resp.setReturnCode(Constant.SUCCESS);
		resp.setErrorMessage("开票成功!");
		return resp;
	}
	
	/**
	 * 订单详情
	 * @param orderid
	 * @return
	 */
	public List<OrderDetailVo> orderDetail(String orderid){
		log.info("【订单详情传入参数】:{}", orderid);
		List<OrderDetailVo> orderResp = new ArrayList<OrderDetailVo>();
		List<OrderDetail> details = orderDetailDao.findByOrderId(orderid);
		
		List<ProductInfo> products = productInfoDao.findAll();
		Map<Integer,ProductInfo> proMap = new HashMap<Integer,ProductInfo>();
		if(products != null && products.size() > 0) {
			for(ProductInfo pro : products) {
				proMap.put(pro.getGoodsId(), pro);
			}
		}
		if(details != null && details.size() > 0) {
			for (OrderDetail order : details) {
				OrderDetailVo vo = new OrderDetailVo();
				BeanUtils.copyProperties(order, vo,new String[]{"createTime", "updateTime","billTime"});
				int goodsid = order.getGoodsId();
				vo.setGoodsThumb(proMap.get(goodsid).getGoodsThumb());
				vo.setCreateTime((DateUtil.format(order.getCreateTime(), "yyyy-MM-dd HH:mm:ss")));
				vo.setUpdateTime((DateUtil.format(order.getUpdateTime(), "yyyy-MM-dd HH:mm:ss")));
				orderResp.add(vo);
			}
		}
		return orderResp;
	}
	
	/**
	 * 获取个人订单
	 * @param openid
	 * @return
	 */
	public List<OrderInfoVo> myOrders(String openid,String type){
		log.info("【我的订单传入参数】:{}", openid);
		List<OrderInfoVo> orderResp = new ArrayList<OrderInfoVo>();
		User user = userDao.findByOpenid(openid);
		if(user != null) {
			int uid = user.getId();
			List<OrderInfo> orderList = null;
			if(!StringUtil.isEmpty(type)) {
				orderList = orderInfoDao.findByUidAndType(uid,type);
			}else {
				orderList = orderInfoDao.findByUid(uid);
			}
			for (OrderInfo order : orderList) {
				OrderInfoVo vo = new OrderInfoVo();
				BeanUtils.copyProperties(order, vo,new String[]{"createTime", "updateTime","billTime"});
				vo.setCreateTime((DateUtil.format(order.getCreateTime(), "yyyy-MM-dd HH:mm:ss")));
				vo.setUpdateTime((DateUtil.format(order.getUpdateTime(), "yyyy-MM-dd HH:mm:ss")));
				orderResp.add(vo);
			}
		}
		return orderResp;
	}
	
	/**
	 * 获取发票列表（在订单表中）
	 * @param openid
	 * @return
	 */
	public List<OrderInfoVo> myBill(String openid){
		log.info("【我的发票传入参数】:{}", openid);
		List<OrderInfoVo> orderResp = new ArrayList<OrderInfoVo>();
		User user = userDao.findByOpenid(openid);
		if(user != null) {
			int uid = user.getId();
			List<OrderInfo> orderList = orderInfoDao.findByUidAndBill(uid);
			for (OrderInfo order : orderList) {
				OrderInfoVo vo = new OrderInfoVo();
				BeanUtils.copyProperties(order, vo,new String[]{"createTime", "updateTime","billTime"});
				vo.setCreateTime((DateUtil.format(order.getCreateTime(), "yyyy-MM-dd HH:mm:ss")));
				vo.setUpdateTime((DateUtil.format(order.getUpdateTime(), "yyyy-MM-dd HH:mm:ss")));
				vo.setBillTime((DateUtil.format(order.getBillTime(), "yyyy-MM-dd HH:mm:ss")));
				orderResp.add(vo);
			}
		}
		return orderResp;
	}
	
	/**
	 * 减积分
	 * @param user
	 * @param socre
	 * @return
	 */
	public Boolean delScore(User user, int socre) {
		boolean flag = false;
		int uScore = user.getScore();
		if (uScore - socre < 0) {
			// 积分不足
			flag = false;
		} else {
			flag = true;
			int nowScore = uScore - socre;
			user.setScore(nowScore);
			userDao.saveAndFlush(user);
		}
		return flag;
	}
	
	/**
	 * 增加购买次数
	 * @param goods
	 * @param number
	 */
	public void addSuccTimes(ProductInfo goods) {
		int succTimes = goods.getSuccTimes();
		goods.setSuccTimes(succTimes + 1);
		productInfoDao.saveAndFlush(goods);
	}
	
	/**
	 * 验证参数
	 * @param user
	 * @param goods
	 * @param form
	 * @return
	 */
	public OrderResp checkParam(User user,ProductInfo goods,OrderInfoForm form) {
		OrderResp resp = new OrderResp();
		int goodsId = form.getGoodsId();
		String openid = form.getOpenid();
		//消耗总积分
		int score = form.getScore();
		if(user != null) {
			String phone = user.getMobile();
			String realName = user.getRealname();
			if(StringUtil.isEmpty(phone) && StringUtil.isEmpty(realName)) {
				resp.setReturnCode(Constant.USERINFO_OVER);
				resp.setErrorMessage("用户信息不完善!");
				log.error("【用户信息不完善】，openid={}",openid);
				return resp;
			}
		}else {
			resp.setReturnCode(Constant.USERNOTEXISTS);
			resp.setErrorMessage("用户不存在!");
			log.error("【用户不存在】，openid={}",openid);
			return resp;
		}
		if(goods == null) {
			resp.setReturnCode(Constant.PRODUCTNOTEXISTS);
			resp.setErrorMessage("商品不存在!");
			log.error("【商品不存在】，goodsId={}",goodsId);
			return resp;
		}
		int uScore = user.getScore();
		if (uScore - score < 0) {
			resp.setReturnCode(Constant.SCORE_NOTENOUGH);
			resp.setErrorMessage("用户积分不足!");
			log.error("【该用户积分不足】,openid:{}",openid);
			return resp;
		}
		resp.setReturnCode(Constant.SUCCESS);
		resp.setErrorMessage("验证通过！");
		return resp;
	}
	
}
