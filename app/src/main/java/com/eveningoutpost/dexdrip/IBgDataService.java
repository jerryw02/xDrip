package com.eveningoutpost.dexdrip; // 请替换为 xDrip 的实际包名

import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface IBgDataService extends IInterface {
    void updateBgData(BgData data) throws RemoteException;
    void registerCallback(IBgDataCallback callback) throws RemoteException;
    void unregisterCallback(IBgDataCallback callback) throws RemoteException;

    abstract class Stub extends android.os.Binder implements IBgDataService {
        private static final String DESCRIPTOR = "com.eveningoutpost.dexdrip.IBgDataService";

        static final int TRANSACTION_updateBgData = (IBinder.FIRST_CALL_TRANSACTION + 0);
        static final int TRANSACTION_registerCallback = (IBinder.FIRST_CALL_TRANSACTION + 1);
        static final int TRANSACTION_unregisterCallback = (IBinder.FIRST_CALL_TRANSACTION + 2);

        public Stub() {
            this.attachInterface(this, DESCRIPTOR);
        }

        public static IBgDataService asInterface(IBinder obj) {
            if ((obj == null)) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (((iin != null) && (iin instanceof IBgDataService))) {
                return ((IBgDataService) iin);
            }
            return new IBgDataService.Stub.Proxy(obj);
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
                case TRANSACTION_updateBgData: {
                    data.enforceInterface(DESCRIPTOR);
                    BgData _arg0;
                    if ((0 != data.readInt())) {
                        _arg0 = BgData.CREATOR.createFromParcel(data);
                    } else {
                        _arg0 = null;
                    }
                    this.updateBgData(_arg0);
                    return true;
                }
                case TRANSACTION_registerCallback: {
                    data.enforceInterface(DESCRIPTOR);
                    IBgDataCallback _arg0;
                    _arg0 = IBgDataCallback.Stub.asInterface(data.readStrongBinder());
                    this.registerCallback(_arg0);
                    return true;
                }
                case TRANSACTION_unregisterCallback: {
                    data.enforceInterface(DESCRIPTOR);
                    IBgDataCallback _arg0;
                    _arg0 = IBgDataCallback.Stub.asInterface(data.readStrongBinder());
                    this.unregisterCallback(_arg0);
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static class Proxy implements IBgDataService {
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
            public void updateBgData(BgData data) throws RemoteException {
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
                    mRemote.transact(Stub.TRANSACTION_updateBgData, _data, _reply, 0);
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public void registerCallback(IBgDataCallback callback) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeStrongBinder((((callback != null)) ? (callback.asBinder()) : (null)));
                    mRemote.transact(Stub.TRANSACTION_registerCallback, _data, _reply, 0);
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public void unregisterCallback(IBgDataCallback callback) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeStrongBinder((((callback != null)) ? (callback.asBinder()) : (null)));
                    mRemote.transact(Stub.TRANSACTION_unregisterCallback, _data, _reply, 0);
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }
    }
}
