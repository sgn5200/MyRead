package com.lansent.testnfc;

import android.nfc.tech.IsoDep;
import android.util.Log;

import java.io.IOException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;

/**
 * Description    :  命令工具  命令返回90 00 表示成功
 * CreateAuthor: Cannan
 * CreateTime   : 2018/9/22     18:53
 * Project          : TestNFC
 * PackageName :  com.lansent.testnfc;
 */

public class NfcCpuUtils {

	/**
	 * 1. 在“COS命令框”输入“00A40000023F00”，然后点击“发送命令”，进入主目录
	 */
	private  final byte[] CMD_START = new byte[]{0x00, (byte) 0xA4, 0x00, 0x00, 0x02, 0x3F, 0x00};    //6f,15,84,e,31,50,41,59,2e,53,59,53,2e,44,44,46,30,31,a5,3,88,1,1,90,0,
	/**
	 * 2. 复合外部认证（秘钥：FFFFFFFFFFFFFFFF，秘钥标识号：00）
	 */
	private  byte[] CMD_KEY = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
	/**
	 * 2.1 获取4位 随机码 {0x00, (byte) 0x84, 0x00, 0x00, 0x04}
	 */
	private final byte[] CMD_GET_RANDOM = {0x00, (byte) 0x84, 0x00, 0x00, 0x04};

	private  final byte[] CMD_DEL = {(byte) 0x80, 0x0E, 0x00, 0x00, 0x00};     //3.删除主目录下的所有文件：800E000000（注意：这个命令会删除主目录下的所有文件）

	//	4. 建立外部认证秘钥    4.1选择根目录（00A4000000）
	// 4.2建密钥文件 (80 E0 00 00 07 3F 00 B0 01 F0 FF FF
	// 4.3创建外部认证密钥 (80 D4 01 00 0D 39 F0F0 AA 55 FFFFFFFFFFFFFFFF)
	private  final byte[] CMD_CREATE_DIR = {0x00, (byte) 0xA4, 0x00, 0x00, 0x02,0x3f,0x00};
	private  final byte[] CMD_CREATE_KEY = {(byte) 0x80, (byte) 0xE0, 0x00, 0x00, 0x07, 0x3F, 0x00, (byte) 0xB0, 0x01, (byte) 0xF0, (byte) 0xFF, (byte) 0xFF};
	private  final byte[] CMD_CREATE_OUT_KEY = {(byte) 0x80, (byte) 0xD4, (byte) 0x01, (byte) 0x00, (byte) 0x0D, (byte)0x39, (byte) 0xF0, (byte) 0xF0, (byte) 0xAA
			, (byte) 0x55, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
	//5 建立访问自定义文件的密钥文件
	private  final byte[] CMD_ACCESS = {(byte) 0x80, (byte) 0xE0, (byte) 0x00, (byte) 0x01, (byte) 0x07, (byte) 0x3F, (byte) 0x01, (byte) 0x8F, (byte) 0x95, (byte) 0xF0, (byte) 0xFF, (byte) 0xFF};
	// 填充密钥123456
	private  final byte[] CMD_ACCESS_INTO = {(byte) 0x80, (byte) 0xD4, (byte) 0x01, (byte) 0x01, (byte) 0x08, (byte) 0x3A, (byte) 0xF0, (byte) 0xEF, (byte) 0x44, (byte) 0x55, (byte) 0x12, (byte) 0x34, (byte) 0x56};
	//6. 创建自定义文件，标识为005(80E000050728000FF4F4FF02)
	private  final byte[] CMD_ACCESS_FILE = {(byte) 0x80, (byte) 0xE0, (byte) 0x00, (byte) 0x05, (byte) 0x07, (byte) 0x28, (byte) 0x00, (byte) 0x0F, (byte) 0xF4, (byte) 0xF4, (byte) 0xFF, (byte) 0x02};
	//7.写数据到文件标识为0005的文件
	//7.1选中该文件（00A40000020005）
	//	7.2写数据“112233445566”到该文件（00D6000006112233445566）
	private  final byte[] CMD_ACCESS_FILE_CHOOICE = {(byte) 0x00, (byte) 0xA4, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x00, (byte) 0x05};
	private final byte[] CMD_ACCESS_FILE_WRITE = {(byte) 0x00, (byte) 0xD6, (byte) 0x00, (byte) 0x00, (byte) 0x06, (byte) 0x88, (byte) 0x88, (byte) 0x88, (byte) 0x44, (byte) 0x55, (byte) 0x66};

	// 声明ISO-DEP协议的Tag操作实例
	private final IsoDep tag;

	public NfcCpuUtils(IsoDep tag) throws IOException {
		// 初始化ISO-DEP协议的Tag操作类实例
		this.tag = tag;
		tag.setTimeout(5000);
		tag.connect();
	}

	public byte[] wirte() throws IOException {
		byte[] resp = tag.transceive(CMD_START);  //1 进入主目录
		if (checkRs(resp)) {
			print("1 进入主目录成功");
			resp = tag.transceive(CMD_GET_RANDOM); //2 获取随机码
			if (checkRs(resp)) {
				print("2 获取随机码");
				byte[] random = {resp[0], resp[1], resp[2], resp[3], 0x00, 0x00, 0x00, 0x00};//3 随机码4个字节+4个字节0
				byte[] desKey;
				try {
					desKey = encrypt(random, CMD_KEY); //4 生产加密后的随机码
					print("3 生产加密后的随机码");
					printByte(desKey);
				} catch (Exception e) {
					e.printStackTrace();
					desKey = null;
				}
				 //00 82 00 00 08 7f cf 90 a0 5b 9c f1 73
				if (desKey != null && desKey.length > 8) {
					byte[] respondKey = {0x00, (byte) 0x82, 0x00, 0x00, 0x08, desKey[0], desKey[1], desKey[2], desKey[3], desKey[4], desKey[5], desKey[6], desKey[7]};
					print("4 生产加密后的随机码命令");
					printByte(respondKey);
					resp = tag.transceive(respondKey); //5 将加密后的随机码发送，注意此处第四字节表示密码标识符00，
				}              
				if (checkRs(resp)) {
					print("5 外部认证成功");
					resp = tag.transceive(CMD_DEL);
					if (checkRs(resp)) {
						print("6 删除目录成功");
						resp = tag.transceive(CMD_CREATE_DIR);
						if (checkRs(resp)) {
							print("7 选择目录");
							resp = tag.transceive(CMD_CREATE_KEY);
							if (checkRs(resp)) {
								print("8 建立目录");
								resp = tag.transceive(CMD_CREATE_OUT_KEY);
								if (checkRs(resp)) {
									print("9 创建外部认证密钥成功");
									resp = tag.transceive(CMD_ACCESS);
									if (checkRs(resp)) {
										print("10 建立访问自定义文件的密钥文件成功");
										resp = tag.transceive(CMD_ACCESS_INTO); //11 填充密钥123456
										if (checkRs(resp)) {
											print("11 填充密钥123456成功");
											resp = tag.transceive(CMD_ACCESS_FILE); //12  创建自定义文件，标识为005
											if (checkRs(resp)) {
												print("12  创建自定义文件，标识为005成功");
												resp = tag.transceive(CMD_ACCESS_FILE_CHOOICE);   // 13  选中该文件0005
												if (checkRs(resp)) {
													print(" 13  选中该文件0005成功");
													resp = tag.transceive(CMD_ACCESS_FILE_WRITE);  //14 写数据“112233445566”到该文件
													if (checkRs(resp)) {       //15 应该有关闭连接
														print("14 写数据“112233445566”到该文件成功");
														return "01".getBytes();
													}
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
		return null;
	}

	private boolean checkRs(byte[] resp) {
		String r = printByte(resp);
		Log.i("---------", "response " + r);
		int status = ((0xff & resp[resp.length - 2]) << 8) | (0xff & resp[resp.length - 1]);
		return status == 0x9000;
	}

	private String printByte(byte[] data) {
		StringBuffer bf = new StringBuffer();

		for (byte b : data) {
			bf.append(Integer.toHexString(b & 0xFF));
			bf.append(",");
		}
		Log.i("TAG", bf.toString());
		return bf.toString();
	}

	private void print(String msg) {
		Log.i("TAG", msg);
	}

	/**
	 * Description 根据键值进行加密
	 * 随机码4个字节+4个字节0
	 *
	 * @param data
	 * @param key  加密键byte数组
	 * @return
	 * @throws Exception
	 */
	public byte[] encrypt(byte[] data, byte[] key) throws Exception {
		// 生成一个可信任的随机数源
		SecureRandom sr = new SecureRandom();

		// 从原始密钥数据创建DESKeySpec对象
		DESKeySpec dks = new DESKeySpec(key);

		// 创建一个密钥工厂，然后用它把DESKeySpec转换成SecretKey对象
		SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
		SecretKey securekey = keyFactory.generateSecret(dks);

		// Cipher对象实际完成加密操作
		Cipher cipher = Cipher.getInstance("DES");

		// 用密钥初始化Cipher对象
		cipher.init(Cipher.ENCRYPT_MODE, securekey, sr);
		return cipher.doFinal(data);
	}
}
