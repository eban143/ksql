package io.confluent.ksql.rest.server.mock;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.TopicPartition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.confluent.ksql.metastore.MetaStoreImpl;
import io.confluent.ksql.parser.tree.Statement;
import io.confluent.ksql.rest.server.computation.Command;
import io.confluent.ksql.rest.server.computation.CommandId;
import io.confluent.ksql.rest.server.computation.CommandIdAssigner;
import io.confluent.ksql.rest.server.computation.CommandStore;
import io.confluent.ksql.rest.server.utils.TestUtils;

public class MockCommandStore extends CommandStore {

  CommandIdAssigner commandIdAssigner;

  private final AtomicBoolean closed;
  private boolean isFirstCall = true;

  public MockCommandStore(String commandTopic,
                          Consumer<CommandId, Command> commandConsumer,
                          Producer<CommandId, Command> commandProducer,
                          CommandIdAssigner commandIdAssigner) {
    super(commandTopic, commandConsumer, commandProducer,
          new CommandIdAssigner(new MetaStoreImpl()));

    commandIdAssigner = new CommandIdAssigner(new MetaStoreImpl());
    closed = new AtomicBoolean(false);
  }

  @Override
  public void close() {
    closed.set(true);
  }

  @Override
  public ConsumerRecords<CommandId, Command> getNewCommands() {
    List<ConsumerRecord<CommandId, Command>> records = new ArrayList<>();
    Map<TopicPartition, List<ConsumerRecord<CommandId, Command>>> recordsMap = new HashMap<>();
    if (isFirstCall) {
      LinkedHashMap<CommandId, Command> commands = new TestUtils().getAllPriorCommandRecords();
      for (CommandId commandId: commands.keySet()) {
        records.add(new ConsumerRecord<CommandId, Command>(
            "T1",10, 100,
            commandId, commands.get(commandId)));
      }

      recordsMap.put(new TopicPartition("T1", 1), records);
      isFirstCall = false;
    } else {
      close();
    }
    return new ConsumerRecords<>(recordsMap);
  }

  @Override
  public CommandId distributeStatement(
      String statementString,
      Statement statement,
      Map<String, Object> streamsProperties
  ) throws Exception {
    CommandId commandId = commandIdAssigner.getCommandId(statement);
    return commandId;
  }

  @Override
  public LinkedHashMap<CommandId, Command> getPriorCommands() {
    return new TestUtils().getAllPriorCommandRecords();
  }

}
