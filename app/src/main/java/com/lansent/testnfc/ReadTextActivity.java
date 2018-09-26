package com.lansent.testnfc;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Arrays;

public class ReadTextActivity extends Activity {
	private TextView mNfcText;
	private String mTagText;
	private NfcAdapter nfcAdapter;
	private String[][] mTechLists;
	private PendingIntent pendingIntent;
	private IntentFilter[] mWriteTagFilters;
	private boolean isRead=true;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_read_text);
		mNfcText = (TextView) findViewById(R.id.tv_nfctext);
		mNfcText.setMovementMethod(ScrollingMovementMethod.getInstance());
		findViewById(R.id.btRead).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Button button = (Button) v;
				if("读".equals(button.getText())){
					button.setText("写");
					isRead=false;
				}else{
					button.setText("读");
					isRead=true;
				}
			}
		});
		initNFC();
	}

	private void initNFC() {
		nfcAdapter = NfcAdapter.getDefaultAdapter(this);
		mTechLists = new String[][] {
				new String[] { MifareClassic.class.getName() },
				new String[] { IsoDep.class.getName(),NfcA.class.getName() } };// 允许扫描的标签类型
		pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
				getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
		ndef.addCategory("*/*");

		// 允许扫描的标签类型
		mWriteTagFilters = new IntentFilter[]{ndef};
	}

	@Override
	protected void onResume() {
		super.onResume();
		nfcAdapter.enableForegroundDispatch(this, pendingIntent, mWriteTagFilters, mTechLists);
	}

	@Override
	protected void onPause() {
		super.onPause();
		nfcAdapter.disableForegroundDispatch(this);
	}

	@Override
	public void onNewIntent(Intent intent) {
		//1.获取Tag对象
		Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		// 获取标签id数组
		byte[] bytesId = detectedTag.getId();

		mNfcText.append("\n\nid=="+bytesToHexString(bytesId)+"\n");
		for(String tag:detectedTag.getTechList()){
			print("tag==="+tag);
		}

		//2.获取Ndef的实例
//		Ndef ndef = Ndef.get(detectedTag);
//		if(ndef!=null){
//			mTagText = ndef.getType() + "\nmaxsize:" + ndef.getMaxSize() + "bytes\n\n"+Arrays.toString(bytesId);
//			mNfcText.append(mTagText);
//			readNfcTag(intent);
//			mNfcText.append(mTagText);
//		}

		MifareClassic mfc = MifareClassic.get(detectedTag);
		if(mfc!=null){//M1卡
			String rs = readTag(detectedTag,mfc,2);
			if(TextUtils.isEmpty(rs)){
				mNfcText.setText("读取失败");
			}else
				mNfcText.append(rs);
		}else {    //芯片卡
			readMyNFC(detectedTag);
		}

	}

	private void readMyNFC(Tag tag) {
		try {
			if(isRead){
				NFC nfc = new NFC(IsoDep.get(tag));
				byte[] resp= nfc.send();
				mNfcText.append("芯片卡读取数据:"+bytesToHexString(resp));
			}else {
				NfcCpuUtils nfc = new NfcCpuUtils(IsoDep.get(tag));
				// 发送取随机数APDU命令
				byte[] resp = nfc.wirte();
				// TextView中显示APDU响应
				mNfcText.append("芯片卡写入数据结果:" + bytesToHexString(resp));
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 读扇区
	 * @return
	 */
	private String readTag(Tag tag,MifareClassic mfc,int sectorIndex){
		for (String tech : tag.getTechList()) {
			System.out.println("------------"+tech);
		}
		//读取TAG
		try {
			String metaInfo = "";
			//Enable I/O operations to the tag from this TagTechnology object.
			mfc.connect();
			int type = mfc.getType();//获取TAG的类型
			int sectorCount = mfc.getSectorCount();//获取TAG中包含的扇区数
			String typeS = "";
			switch (type) {
				case MifareClassic.TYPE_CLASSIC:
					typeS = "TYPE_CLASSIC";
					break;
				case MifareClassic.TYPE_PLUS:
					typeS = "TYPE_PLUS";
					break;
				case MifareClassic.TYPE_PRO:
					typeS = "TYPE_PRO";
					break;
				case MifareClassic.TYPE_UNKNOWN:
					typeS = "TYPE_UNKNOWN";
					break;
			}
			metaInfo += "卡片类型：" + typeS + "\n共" + sectorCount + "个扇区\n共" 	+ mfc.getBlockCount() + "个块\n存储空间: " + mfc.getSize() + "B\n";
			int blockIndex;
			if (mfc.authenticateSectorWithKeyA(sectorIndex, new byte[]{0x42,0x53,0x4B, (byte) sectorIndex,0x4C,0x53}) ) {
				blockIndex = mfc.sectorToBlock(sectorIndex);
				byte[] data = mfc.readBlock(blockIndex);
				metaInfo += "旧卡 Block " + blockIndex + " : " + new String(data) + "\n";
			}else if( mfc.authenticateSectorWithKeyA(sectorIndex, MifareClassic.KEY_NFC_FORUM)){
				blockIndex = mfc.sectorToBlock(sectorIndex);
				byte[] data = mfc.readBlock(blockIndex);
				metaInfo += "新卡 Block " + blockIndex + " : " + new String(data) + "\n";

			}else {
				metaInfo += "Sector " + sectorIndex + ":验证失败\n";
			}
			return metaInfo;
		} catch (Exception e) {
			Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
			e.printStackTrace();
		} finally {
			if (mfc != null) {
				try {
					mfc.close();
				} catch (IOException e) {
					Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG)
							.show();
				}
			}
		}
		return null;
	}

	/**
	 * Convert byte[] to hex string
	 *
	 * @param src byte[] data
	 * @return hex string
	 */
	public static String bytesToHexString(byte[] src){
		StringBuilder stringBuilder = new StringBuilder("");
		if (src == null || src.length <= 0) {
			return null;
		}
		for (int i = 0; i < src.length; i++) {
			int v = src[i] & 0xFF;
			String hv = Integer.toHexString(v);
			if (hv.length() < 2) {
				stringBuilder.append(0);
			}
			stringBuilder.append(hv);
		}
		return stringBuilder.toString();
	}


	/**
	 * 读取NFC标签文本数据
	 */
	private void readNfcTag(Intent intent) {
		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())||
				NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
			Parcelable[] rawMsgs = intent.getParcelableArrayExtra(
					NfcAdapter.EXTRA_NDEF_MESSAGES);
			NdefMessage msgs[] = null;
			int contentSize = 0;
			if (rawMsgs != null) {
				msgs = new NdefMessage[rawMsgs.length];
				for (int i = 0; i < rawMsgs.length; i++) {
					msgs[i] = (NdefMessage) rawMsgs[i];
					contentSize += msgs[i].toByteArray().length;
				}
			}
			try {
				if (msgs != null) {
					print(msgs.length+" 长度");
					NdefRecord record = msgs[0].getRecords()[0];
					String textRecord = parseTextRecord(record);
					mTagText += textRecord + "\n\ntext\n" + contentSize + " bytes";
					print(mTagText);
				}
			} catch (Exception e) {
			}
		}
	}

	private void print(String msg){
		Log.i("NFCREAD",msg);
	}
	/**
	 * 解析NDEF文本数据，从第三个字节开始，后面的文本数据
	 * @param ndefRecord
	 * @return
	 */
	public static String parseTextRecord(NdefRecord ndefRecord) {
		/**
		 * 判断数据是否为NDEF格式
		 */
		//判断TNF
		if (ndefRecord.getTnf() != NdefRecord.TNF_WELL_KNOWN) {
			return null;
		}
		//判断可变的长度的类型
		if (!Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
			return null;
		}
		try {
			//获得字节数组，然后进行分析
			byte[] payload = ndefRecord.getPayload();
			//下面开始NDEF文本数据第一个字节，状态字节
			//判断文本是基于UTF-8还是UTF-16的，取第一个字节"位与"上16进制的80，16进制的80也就是最高位是1，
			//其他位都是0，所以进行"位与"运算后就会保留最高位
			String textEncoding = ((payload[0] & 0x80) == 0) ? "UTF-8" : "UTF-16";
			//3f最高两位是0，第六位是1，所以进行"位与"运算后获得第六位
			int languageCodeLength = payload[0] & 0x3f;
			//下面开始NDEF文本数据第二个字节，语言编码
			//获得语言编码
			String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
			//下面开始NDEF文本数据后面的字节，解析出文本
			String textRecord = new String(payload, languageCodeLength + 1,
					payload.length - languageCodeLength - 1, textEncoding);
			return textRecord;
		} catch (Exception e) {
			throw new IllegalArgumentException();
		}
	}
}