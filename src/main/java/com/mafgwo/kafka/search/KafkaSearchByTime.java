package com.mafgwo.kafka.search;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * kafka 查找某时间段内数据
 *
 * @author chenxiaoqi
 * @since 2021/06/12
 */
public class KafkaSearchByTime {

    static KafkaConsumer<String, String> consumer;

    public static void main(String[] args) throws Exception {
        //传入参数判断
        if (args.length < 3) {
            throw new Exception("parameter args exception!");
        }
        String filterValue = "";
        if (args.length > 3) {
            filterValue = args[3];
        }
        long fetchStartTime = 0;
        long fetchEndTime = 0;
        try {
            fetchStartTime = dateToStamp(args[1]);
            fetchEndTime = dateToStamp(args[2]);
        } catch (ParseException e) {
            throw new Exception(e.toString());
        }
        if (fetchStartTime == 0 || fetchEndTime == 0) {
            throw new Exception("startTime|endTime error!");
        }

        // kafkaConsumer
        Properties props = new Properties();
        // 连接地址
        props.put("bootstrap.servers", "10.0.32.62:9092,10.0.32.63:9092,10.0.32.64:9092");
        props.put("group.id", "kafka_search_test");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumer = new KafkaConsumer<>(props);

        //根据时间段及过滤条件获取指定数据
        getMsgByTime(args[0], fetchStartTime, fetchEndTime, filterValue);
        System.out.println("finish!");
    }

    private static void getMsgByTime(String topic, long fetchStartTime, long fetchEndTime, String filterValue) {
        //根据起始时间获取每个分区的起始offset
        Map<TopicPartition, Long> map = new HashMap<>();
        List<PartitionInfo> partitions = consumer.partitionsFor(topic);
        for (PartitionInfo par : partitions) {
            map.put(new TopicPartition(topic, par.partition()), fetchStartTime);
        }
        Map<TopicPartition, OffsetAndTimestamp> parMap = consumer.offsetsForTimes(map);

        //遍历每个分区，将不同分区的数据写入不同文件中
        boolean filterCondition1 = filterValue.trim().length() == 0;
        boolean filterCondition = true, isBreak = false;
        for (Map.Entry<TopicPartition, OffsetAndTimestamp> entry : parMap.entrySet()) {
            TopicPartition key = entry.getKey();
            OffsetAndTimestamp value = entry.getValue();
//            System.out.println(key);//topic-partition,eg:testTopic-0
//            System.out.println(value);//eg:(timestamp=1584684100465, offset=32382989)
            // 根据消费里的timestamp确定offset
            if (value != null) {
                long offset = value.offset();
                // // 订阅主题中指定的分区key.partition()
                consumer.assign(Arrays.asList(key));
                consumer.seek(key, offset);
            }

            //拉取消息
            isBreak = false;
            FileWriter fw = null;
            try {
                fw = new FileWriter(new File("./" + key.toString() + ".txt"), true);
            } catch (IOException e) {
                e.printStackTrace();
            }
            while (true) {
                ConsumerRecords<String, String> poll = consumer.poll(1000);
                StringBuffer stringBuffer = new StringBuffer(20000);
                for (ConsumerRecord<String, String> record : poll) {
                    filterCondition = filterCondition1 || (filterValue.trim().length() > 0 && record.value().contains(filterValue));
                    if (record.timestamp() <= fetchEndTime && filterCondition) {
                        stringBuffer.append(record.value() + "\r\n");
                    } else if (record.timestamp() > fetchEndTime) {
                        isBreak = true;
                    }
                }
                try {
                    fw.write(stringBuffer.toString());
                    stringBuffer.setLength(0);
                    fw.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (isBreak) {
                    break;
                }
            }
            try {
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 将时间转换为时间戳
     * @param s
     * @return
     * @throws ParseException
     */
    private static Long dateToStamp(String s) throws ParseException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = simpleDateFormat.parse(s);
        return date.getTime();
    }

}
