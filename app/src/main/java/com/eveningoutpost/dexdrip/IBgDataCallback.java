package com.eveningoutpost.dexdrip; // 请替换为 xDrip 的实际包名

import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import com.eveningoutpost.dexdrip.BgData;

public interface IBgDataCallback extends IInterface {
    void onBgDataReceived(BgData data) throws RemoteException;

    abstract class Stub extends android.os.Binder implements IBgDataCallback {
        private static final String DESCRIPTOR = "com.eveningoutpost.dexdrip.IBgDataCallback";

        static final int TRANSACTION_onBgDataReceived = (IBinder.FIRST_CALL_TRANSACTION + 0);

        public Stub() {
            this.attachInterface(this, DESCRIPTOR);
        }

        public static IBgDataCallback asInterface(IBinder obj) {
            if ((obj == null)) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (((iin != null) && (iin instanceof IBgDataCallback))) {
                return ((IBgDataCallback) iin);
            }
            return new IBgDataCallback.Stub.Proxy(obj);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION: {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                case TRANSACTION_onBgDataReceived: {
                    data.enforceInterface(DESCRIPTOR);
                    BgData _arg0;
                    if ((0 != data.readInt())) {
                        _arg0 = BgData.CREATOR.createFromParcel(data);
                    } else {
                        _arg0 = null;
                    }
                    this.onBgDataReceived(_arg0);
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static class Proxy implements IBgDataCallback {
            private IBinder mRemote;

            Proxy(IBinder remote) {
                mRemote = remote;
            }

            @Override
            public IBinder asBinder() {
                return mRemote;
            }

            public java.lang.String getInterfaceDescriptor() {
                return DESCRIPTOR;
            }

            @Override
            public void onBgDataReceived(BgData data) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    if ((data != null)) {
                        _data.writeInt(1);
                        data.writeToParcel(_data, 0);
                    } else {
                        _data.writeInt(0);
                    }
                    mRemote.transact(Stub.TRANSACTION_onBgDataReceived, _data, _reply, 0);
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }
    }
}
