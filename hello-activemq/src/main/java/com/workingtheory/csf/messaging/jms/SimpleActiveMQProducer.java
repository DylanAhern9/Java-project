package com.workingtheory.csf.messaging.jms;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.io.Serializable;

/**
 * This class demonstrates a single-class implementation of simple ActiveMQ message producer.
 *
 * @param <T> Generic type argument for ActiveMQ messages produced by this producer implementation.
 *            Type T must be an instance of {@link Serializable}.
 */
public class SimpleActiveMQProducer<T extends Serializable>
		implements AutoCloseable
{
	private static final Logger logger = LogManager.getLogger(SimpleActiveMQProducer.class);

	private final String brokerURL;
	private final String queueName;

	private ActiveMQConnection connection;
	private Session            session;
	private MessageProducer    producer;

	/**
	 * Refer following documentation for valid broker URL formats -
	 *
	 * 1. <a href="http://activemq.apache.org/configuring-version-5-transports.html">Configuring transports</a>
	 * 2. <a href="http://activemq.apache.org/failover-transport-reference.html">Failover transports</a>
	 *
	 * @param brokerURL A valid URL to connect to ActiveMQ broker
	 * @param queueName An alpha-numeric name of the ActiveMQ queue, on which producer will be publishing
	 */
	public SimpleActiveMQProducer(String brokerURL, String queueName)
	{
		this.brokerURL = brokerURL;
		this.queueName = queueName;
	}

	/**
	 * Initializes connection factory, connection, session, message queue and message producer instances.
	 *
	 * @throws JMSException when -
	 *                      1. The broker URL format is incorrect
	 *                      2. The broker is started without 'failover' option and server is not running
	 *                      3. The connection factory is unable to create a connection
	 */
	private void initialize() throws JMSException
	{
		// Creating connection factory instance
		ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerURL);

		// Creating connection
		connection = (ActiveMQConnection) factory.createConnection();

		/**
		 * @since version 5.13.1, ActiveMQ requires trusted packages to be explicitly specified,
		 * while sending serializable classes as object message. This can be very annoying,
		 * if you are deploying in a controlled environment.
		 *
		 * It is better to 'set trust all packages' to true so that all classes are trusted,
		 * by default.
		 */
		// Marking all packages as trusted
		connection.setTrustAllPackages(true);

		// Starting the connection to ActiveMQ server
		connection.start();

		/**
		 * This option enables batched delivery of message acknowledgement,
		 * significantly improving message delivery performance.
		 */
		// Setting policy to send message acknowledgements in optimized manner
		connection.setOptimizeAcknowledge(true);

		/**
		 * A session can have several acknowledgement modes based on use case requirements.
		 *
		 * 1. Use {@link Session.AUTO_ACKNOWLEDGE} to let ActiveMQ broker handle the message acknowledgements.
		 * The message acknowledgements are sent as soon as the message is received by the broker,
		 * irrespective of whether the broker succeeds in processing the message.
		 *
		 * 2. Use {@link Session.CLIENT_ACKNOWLEDGE} to allow the consumer to explicitly send message acknowledgements.
		 * This option allows the consumer to process the message before sending the acknowledgement.
		 * In case the message processing fails for some reason, the message can be redelivered to some another consumer.
		 * Use this option with little caution considering unacknowledged message will be redelivered which might cause
		 * unexpected behaviour if duplicate messages are not properly handled.
		 *
		 * 3. Option {@link Session.DUPS_OK_ACKNOWLEDGE} is similar to {@link Session.AUTO_ACKNOWLEDGE} but the acknowledgements
		 * are batched to improve performance. This option is suitable for most of the common use cases.
		 *
		 * 4. Use {@link Session.SESSION_TRANSACTED} option while working in a container based transactions e.g. JTA or XA
		 */
		// Creating session
		session = connection.createSession(false, Session.DUPS_OK_ACKNOWLEDGE);

		// Creating queue instance corresponding to queueName
		Queue queue = session.createQueue(queueName);

		// Creating producer instance
		producer = session.createProducer(queue);

		/**
		 * This controls the persistence behaviour enqueued messages.
		 *
		 * Setting delivery mode to {@link DeliveryMode.NON_PERSISTENT} will make broker to maintain all messages in the memory.
		 * Undelivered messages in the queue will not survive the broker restart. If messages need to survive the broker restart or possible crash,
		 * then set delivery mode to {@link DeliveryMode.PERSISTENT}.
		 *
		 */
		// Setting message persistence
		producer.setDeliveryMode(DeliveryMode.PERSISTENT);
	}

	/**
	 * Initializes and starts the message producer.
	 *
	 * @throws JMSException when initialization, of the message producer, is not successful.
	 */
	public void start() throws JMSException
	{
		// Initializing producer
		this.initialize();
	}

	/**
	 * Sends a {@link Message} to the producer queue.
	 *
	 * @param message to be sent to the producer queue. This parameter cannot be null.
	 *
	 * @throws JMSException when -
	 *                      1. The producer or its corresponding session is closed.
	 *                      2. The broker is down and connection factory is not configured with 'failover' option.
	 */
	private void send(Message message) throws JMSException
	{
		producer.send(message);
	}

	/**
	 * Sends a generic java POJO as {@link Message}.
	 *
	 * @param object to be sent to producer queue. This parameter cannot be null.
	 *
	 * @throws JMSException when -
	 *                      1. The producer or its corresponding session is closed.
	 *                      2. The broker is down and connection factory is not configured with 'failover' option.
	 */
	public void send(T object) throws JMSException
	{
		producer.send(object instanceof String ? session.createTextMessage((String) object) : session.createObjectMessage(object));
	}


	/**
	 * Closes all the resources held by this instance of message producer.
	 */
	public void close()
	{
		JMSUtil.close(producer);
		JMSUtil.close(session);
		JMSUtil.close(connection);
	}
}
