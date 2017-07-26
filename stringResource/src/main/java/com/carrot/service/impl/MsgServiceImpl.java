package com.carrot.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.carrot.dao.StringMsgDao;
import com.carrot.model.StringMsg;
import com.carrot.service.MsgService;

/**
 * 
 * @author curry.su
 *
 */
@Service
public class MsgServiceImpl implements MsgService {

	@Autowired
	private StringMsgDao dao;

	@Override
	public List<StringMsg> getMsgList(String str) {
		// TODO Auto-generated method stub
		return dao.getMsgList(str);
	}

	@Override
	public void insertMsg(StringMsg stm) {
		// TODO Auto-generated method stub
		dao.insertMsg(stm);
	}

}
