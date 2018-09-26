package com.lansent.testnfc;

import android.nfc.tech.IsoDep;
import android.util.Log;

import java.io.IOException;

/**
 * Description    :
 * CreateAuthor: Cannan
 * CreateTime   : 2018/9/22     18:53
 * Project          : TestNFC
 * PackageName :  com.lansent.testnfc;
 */

public class NFC {
	private static final byte[] GET_INIT={0x55,0x55,0,0,0,4,7,1,0x52,0x50};//55 55 00 00 00 04 07 01 52 50
	/**
	 * 1. 在“COS命令框”输入“00A40000023F00”，然后点击“发送命令”，进入主目录
	 */
	private static final byte[] GET_RANDOM = new byte[]{0x00, (byte) 0xA4,0x00,0x00,0x02,0x3F,0x00};    //6f,15,84,e,31,50,41,59,2e,53,59,53,2e,44,44,46,30,31,a5,3,88,1,1,90,0,

	private static byte[]myPsw = { 0x00,0x20,0x00,0x01,0x03,0x12,0x34,0x56};
	private static final byte[]GET_MSG  = {(byte) 0x00,(byte) 0xA4,(byte) 0x00,(byte) 0x00,(byte) 0x02,(byte) 0x00,(byte) 0x05};//00A40000020005
	private static final byte[]GET_MSG2  = {(byte) 0x00,(byte) 0xB0,(byte) 0x00,(byte) 0x00,(byte) 0x00,(byte) 0x00};//00B0 00 00 00 00

	// 声明ISO-DEP协议的Tag操作实例
	private final IsoDep tag;

	public NFC(IsoDep tag) throws IOException {
		// 初始化ISO-DEP协议的Tag操作类实例
		this.tag = tag;
		tag.setTimeout(5000);
		tag.connect();
	}

	/**
	 * 向Tag发送获取随机数的APDU并返回Tag响应
	 * @return 十六进制随机数字符串
	 * @throws IOException
	 */
	public byte[] send() throws IOException {
		// 发送APDU命令
		byte[] resp = tag.transceive(GET_RANDOM);   //1. 在“COS命令框”输入“00A40000023F00”，然后点击“发送命令”，进入主目录。
		// 获取NFC Tag返回的状态值
		if(checkRs(resp)){
			resp= tag.transceive(myPsw);    //2. 在“秘钥”框输入“FFFFFFFFFFFFFFFF”，在“秘钥标识号”输入“00”，然后点击“复合外部认证”。
			if(checkRs(resp)){
//				resp= tag.transceive(GET_PSW2);  //3. 验证口令 在“COS命令框”输入“0020000103123456”，验证口令，返回9000说明验证ok。
//				if(checkRs(resp)){
					resp= tag.transceive(GET_MSG); //4 读具体文件内容在“COS命令框”输入“00A40000020005”，返回 9000 表示选中该0005文件。
					if(checkRs(resp)){
						resp= tag.transceive(GET_MSG2);   //4.2 上面的步骤已经选定了需要操作那个文件，然后在“COS命令框”输入“00B000000000”
						if(checkRs(resp)){
							return resp;
						}
					}
			}
		}
		return null;
	}

	private boolean checkRs(byte[] resp){
		String r = printByte(resp);
		Log.i("---------","response "+r);
		int status = ((0xff & resp[resp.length - 2]) << 8) | (0xff & resp[resp.length - 1]);
		return status == 0x9000;
	}

	private String printByte(byte[] data){
		StringBuffer bf= new StringBuffer();
		for(byte b:data){
			bf.append(Integer.toHexString(b & 0xFF));
			bf.append(",");
		}
		return bf.toString();
	}
}
