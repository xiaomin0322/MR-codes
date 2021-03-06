package com.emar.recsys.user.ad;

import java.text.ParseException;
import java.util.List;

import com.emar.recsys.user.util.IUserException;
import com.emar.recsys.user.util.UtilStr;

/**
 * 数据基础类
 * @author zhoulm
 *
 */
public class ADBase {
	
	protected String ad;
	protected List<String> flist;
	
	public ADBase(String ad, List<String> flist) throws ParseException {
		if(ad == null || ad.length() == 0 || !UtilStr.isDigital(ad) || flist == null) {
			throw new ParseException("bad ad or feature list.", IUserException.ErrUrlNull);
		}
		this.ad = ad;
		this.flist = flist;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
