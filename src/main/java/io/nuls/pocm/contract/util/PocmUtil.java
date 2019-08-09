/**
 * MIT License
 * <p>
 * Copyright (c) 2017-2018 nuls.io
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.nuls.pocm.contract.util;

import io.nuls.contract.sdk.Address;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 工具类
 * @author: Long
 * @date: 2019-03-15
 */
public class PocmUtil {

    public final static BigInteger ONE_NULS = BigInteger.valueOf(100000000L);

    public final static BigDecimal LOCKED_PERCENT= new BigDecimal("0.1");

    public static BigDecimal toNuls(BigInteger na) {
        return new BigDecimal(na).movePointLeft(8);
    }

    public static BigInteger toNa(BigDecimal nuls) {
        return nuls.scaleByPowerOfTen(8).toBigInteger();
    }

    /**
     * 检查空投数组的数额是否正确
     * @param receiverAmount
     * @return
     */
    public static boolean checkAmount(long[] receiverAmount){
        boolean result=true;
        for(int i=0;i<receiverAmount.length;i++){
            if( receiverAmount[i]< 0){
                result=false;
                break;
            }
        }
        return result;
    }

    /**
     * 计算空投数组的总额
     * @param receiverAmount
     * @return
     */
    public static BigInteger sumAmount(long[] receiverAmount){
        BigInteger amount = BigInteger.ZERO;
        if(receiverAmount.length>0){
            for(int i=0;i<receiverAmount.length;i++){
                amount = amount.add(BigInteger.valueOf((receiverAmount[i])));
         }
        }
        return amount;
    }

    /**
     * 将空投地址数组转换格式
     * @param receiveraddresses
     * @return
     */
    public static Address[] convertStringToAddres(String[] receiveraddresses){
        Address[] addresses = new Address[receiveraddresses.length];
        for(int i=0;i<receiveraddresses.length;i++){
            Address address = new Address(receiveraddresses[i]);
            addresses[i]=address;
        }
        return addresses;
    }

    private static boolean isNumeric(String str){
        for(int i=0;i<str.length();i++){
            int chr=str.charAt(i);
            if(chr<48||chr>57){
                return false;
            }

        }
        return true;
    }

    public static boolean canConvertNumeric(String str,String maxValue){
        String trimStr=str.trim();
        if(isNumeric(trimStr)){
            if(trimStr.length()<maxValue.length()){
                return true;
            }else if(trimStr.length()==maxValue.length()){
                return trimStr.compareTo(maxValue)<=0;
            }else{
                return false;
            }
        }else{
            return false;
        }
    }

    public static boolean  checkValidity(String str){
        if(str==null){
            return false;
        }
        String strTmp =str.trim();
        if(strTmp.length()>0 &&strTmp.length()<21){
            if(strTmp.endsWith("_")||strTmp.startsWith("_")){
                return false;
            }
            for(int i=0;i<strTmp.length();i++){
                int chr=strTmp.charAt(i);
                if(chr<48||(chr>57&&chr<65)||(chr>90&&chr<95)||(chr>95&&chr<97)||chr>122){
                    return false;
                }
            }
            return true;
        }else{
            return false;
        }
    }

}
