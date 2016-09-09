package org.keedio.flume.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class TestUtils {

    private static final Random rand = new Random();


    // get a static class value
    public static Object reflectValue(Class<?> classToReflect, String fieldNameValueToFetch) throws Exception {

        Field reflectField  = reflectField(classToReflect, fieldNameValueToFetch);
        reflectField.setAccessible(true);
        Object reflectValue = reflectField.get(classToReflect);
        return reflectValue;
    }


    // get an instance value
    public static Object reflectValue(Object objToReflect, String fieldNameValueToFetch) throws Exception {
        Field reflectField  = reflectField(objToReflect.getClass(), fieldNameValueToFetch);
        Object reflectValue = reflectField.get(objToReflect);
        return reflectValue;
    }


    // find a field in the class tree
    public static Field reflectField(Class<?> classToReflect, String fieldNameValueToFetch) throws Exception {

        Field reflectField = null;
        Class<?> classForReflect = classToReflect;
        do {
            try {
                reflectField = classForReflect.getDeclaredField(fieldNameValueToFetch);
            } catch (NoSuchFieldException e) {
                classForReflect = classForReflect.getSuperclass();
            }
        } while (reflectField==null || classForReflect==null);
        reflectField.setAccessible(true);
        return reflectField;

    }


    public static Method reflectMethod(Class<?> classToReflect, String methodNameToFech, Class<?>[] argsGenerateAgentElements) throws Exception {

        Method reflectMethod = null;
        Class<?> classForReflect = classToReflect;
        do {
            try {
                reflectMethod = classForReflect.getDeclaredMethod(methodNameToFech, argsGenerateAgentElements);
            } catch (NoSuchMethodException e) {
                classForReflect = classForReflect.getSuperclass();
            }
        } while (reflectMethod==null || classForReflect==null);
        reflectMethod.setAccessible(true);
        return reflectMethod;

    }


    // set a value with no setter
    public static void refectSetValue(Object objToReflect, String fieldNameToSet, Object valueToSet) throws Exception {
        Field reflectField  = reflectField(objToReflect.getClass(), fieldNameToSet);
        reflectField.set(objToReflect, valueToSet);
    }

    public static void reflectExecuteMethod(Object objToReflect, String methodNameToInvoke, Object[] argsMethodInvoke, Class<?>[] parameterTypes) throws Exception {
        Method reflectMethod = reflectMethod(objToReflect.getClass(), methodNameToInvoke, parameterTypes);
        reflectMethod.invoke(objToReflect, argsMethodInvoke);

    }

    public static Object reflectExecuteMethod(Object objToReflect, String methodNameToInvoke, Class<?>[] parameterTypes, Object... argsMethodInvoke) throws Exception {

        Object result = null;

        Method reflectMethod = reflectMethod(objToReflect.getClass(), methodNameToInvoke, parameterTypes);
        result = reflectMethod.invoke(objToReflect, argsMethodInvoke);

        return result;

    }


    public static <T> LinkedList<T> riffleShuffleLists(List<T> list1, List<T> list2) {

        LinkedList<T> newList = new LinkedList<T>();

        int sizeList1 = list1.size();
        int sizeList2 = list2.size();

        if (sizeList1 == sizeList2) {
            newList = new LinkedList<T>(list1);
            newList.addAll(list2);
            newList =  riffleShuffle(newList, 1, true, false);
            return newList;
        } else if (sizeList1 > sizeList2) {
            int difference = sizeList1 - sizeList2;
            for (int i=0; i<difference; i++) {
                list2.add(null);
            }
        } else if (sizeList1 < sizeList2) {
            int difference = sizeList2 - sizeList1;
            for (int i=0; i<difference; i++) {
                list1.add(null);
            }
        }

        newList = new LinkedList<T>(list1);
        newList.addAll(list2);
        newList =  riffleShuffle(newList, 1, true, false);
        newList.removeAll(Collections.singleton(null));

        return newList;
    }


    public static <T> LinkedList<T> riffleShuffle(List<T> list, int flips, boolean perfectCutting, boolean perfectRiffling){
        LinkedList<T> newList = new LinkedList<T>();

        newList.addAll(list);

        for(int n = 0; n < flips; n++){

            int cutPoint = newList.size() / 2 ;
            if (!perfectCutting) {
                int offset = (int)(newList.size() * 0.1);
                if (offset > 0) {
                    cutPoint = cutPoint + (rand.nextBoolean() ? -1 : 1 ) * rand.nextInt(offset);
                }
            }

            //split the deck
            List<T> left = new LinkedList<T>();
            left.addAll(newList.subList(0, cutPoint));
            List<T> right = new LinkedList<T>();
            right.addAll(newList.subList(cutPoint, newList.size()));

            newList.clear();

            while(left.size() > 0 && right.size() > 0){
                if (perfectRiffling) {
                    newList.add(right.remove(0));
                    newList.add(left.remove(0));
                } else {
                    if(rand.nextDouble() >= ((double)left.size() / right.size()) / 2){
                        newList.add(right.remove(0));
                    }else{
                        newList.add(left.remove(0));
                    }
                }
            }

            //if either hand is out of cards then flip all of the other hand to the shuffled deck
            if(left.size() > 0) newList.addAll(left);
            if(right.size() > 0) newList.addAll(right);
        }
        return newList;
    }

}
