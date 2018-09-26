package com.lansent.testnfc;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Locale;

public class MainActivity extends Activity implements View.OnClickListener {
	private IntentFilter[] mWriteTagFilters;
	private NfcAdapter nfcAdapter;
	private PendingIntent pendingIntent;
	private String[][] mTechLists;
	private String TAG = getClass().getSimpleName();

	//格式化并将信息写入标签
	private NdefRecord[] records;
	private NdefMessage ndefMessage;
	private String write = "光能写入文本数据";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		findViewById(R.id.btRead).setOnClickListener(this);
		initNFC();
		records = new NdefRecord[]{createTextRecord(write)};
		ndefMessage = new NdefMessage(records);
	}

	/**
	 * 创建record，格式为TNF_WELL_KNOWN with RTD_TEXT
	 *
	 * @param payload 你要写入的数据
	 */
	private NdefRecord createTextRecord(String payload) {
		byte[] langBytes = Locale.getDefault().getLanguage().getBytes(Charset.forName("US-ASCII"));
		Charset utfEncoding = Charset.forName("UTF-8"); //encodeInUtf8 ? Charset.forName("UTF-8") : Charset.forName("UTF-16");
		byte[] textBytes = payload.getBytes(utfEncoding);
		int utfBit = 0;//encodeInUtf8 ? 0 : (1 << 7);
		char status = (char) (utfBit + langBytes.length);
		byte[] data = new byte[1 + langBytes.length + textBytes.length];
		data[0] = (byte) status;
		System.arraycopy(langBytes, 0, data, 1, langBytes.length);
		System.arraycopy(textBytes, 0, data, 1 + langBytes.length, textBytes.length);
		NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], data);
		return record;
	}

	private void initNFC() {
		// 获取nfc适配器，判断设备是否支持NFC功能
		nfcAdapter = NfcAdapter.getDefaultAdapter(this);
		if (nfcAdapter == null) {
			shotToast("当前设备不支持NFC功能");
		} else if (!nfcAdapter.isEnabled()) {
			shotToast("NFC功能未打开，请先开启后重试！");
		}
		pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
				getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
		ndef.addCategory("*/*");
		// 允许扫描的标签类型
		mWriteTagFilters = new IntentFilter[]{ndef};
		mTechLists = new String[][]{
				new String[]{MifareClassic.class.getName()},
				new String[]{NfcA.class.getName()}};// 允许扫描的标签类型
	}

	@Override
	protected void onResume() {
		super.onResume();
		//开启前台调度系统
		nfcAdapter.enableForegroundDispatch(this, pendingIntent, mWriteTagFilters, mTechLists);
	}


	@Override
	protected void onPause() {
		super.onPause();
		nfcAdapter.disableForegroundDispatch(this);
	}

	@Override
	public void onClick(View v) {
		if (v.getId() == R.id.btRead) {
			startActivity(new Intent(this, ReadTextActivity.class));
		}
	}


	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		//当该Activity接收到NFC标签时，运行该方法
		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction()) ||
				NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
			Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);


			boolean readType1 = writeTAG(tag,2);
				Log.i(TAG,"扇区写入成功"+readType1);

//			Ndef ndef = Ndef.get(tag);
//
//			if (ndefMessage == null) {
//				shotToast("没有消息写入");
//				return;
//			}
//			try {
//				boolean rs = writeMsg(ndef, tag, ndefMessage);
//				shotToast(String.format("写入%s", (rs ? "成功" : "失败")));
//			} catch (IOException e) {
//				e.printStackTrace();
//				shotToast("IO异常，读写失败");
//			} catch (FormatException e) {
//				e.printStackTrace();
//				shotToast("格式化异常,读写失败");
//			} catch (NullPointerException e) {
//				shotToast("格NullPointerException异常,读写失败");
//			}
		}
	}

	/**
	 * 扇区读写
	 * @param tag
	 * @param sectorIndex  扇区索引  一般16个扇区 64块
	 * @return
	 */
	public boolean writeTAG(Tag tag,int sectorIndex) {
		MifareClassic mfc = MifareClassic.get(tag);
		try {
			mfc.connect();
			if (mfc.authenticateSectorWithKeyA(sectorIndex, new byte[]{0x42,0x53,0x4B, (byte) sectorIndex,0x4C,0x53})) {   //已知密码认证    r
				// the last block of the sector is used for KeyA and KeyB cannot be overwritted
				int block = mfc.sectorToBlock(sectorIndex);
				mfc.writeBlock(block, "sgn-old000000000".getBytes());
				mfc.close();
				shotToast("旧卡 写入成功");
				return true;
			}else if(mfc.authenticateSectorWithKeyA(sectorIndex, MifareClassic.KEY_NFC_FORUM)){     //新卡 未设密码认证  r
				int block = mfc.sectorToBlock(sectorIndex);
				mfc.writeBlock(block, "SGN-new000000000".getBytes());
				mfc.close();
				shotToast("新卡 写入成功");
			} else{
				shotToast("未认证");
			}
		} catch (IOException e) {
			e.printStackTrace();
			shotToast("扇区连接异常");

			try {
				mfc.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
		return false;
	}


	/**
	 * NFC m1
	 *
	 * @param ndef
	 * @param tag
	 * @param ndefMessage
	 * @return
	 * @throws IOException
	 * @throws FormatException
	 */
	private boolean writeMsg(Ndef ndef, Tag tag, NdefMessage ndefMessage) throws IOException, FormatException {
		try {
			if (ndef == null) {
				shotToast("格式化数据开始");
				//Ndef格式类
				NdefFormatable format = NdefFormatable.get(tag);
				format.connect();
				format.format(ndefMessage);
			} else {
				shotToast("写入数据开始");
				//数据的写入过程一定要有连接操作
				ndef.connect();
				ndef.writeNdefMessage(ndefMessage);
			}
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			shotToast("IO异常，读写失败");
		} catch (FormatException e) {
			e.printStackTrace();
			shotToast("格式化异常,读写失败");
		} catch (NullPointerException e) {
			shotToast("格NullPointerException异常,读写失败");
		}catch (IllegalStateException e){
			shotToast("Close other technology first!");
		}
		return false;
	}

	private Toast toast;

	private void shotToast(String msg) {
		Log.i(TAG,msg);
		if (toast == null) {
			toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
			toast.show();
		} else {
			toast.setText(msg);
			toast.show();
		}
	}

}
