package com.workingtheory.csf.messaging.jms;

import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.ObjectMessage;
import javax.jms.TextMessage;
import java.io.Serializable;

/**
 * This class demonstrates the tiered implementation of message consumer.
 * {@link ActiveMQMessageHandler} is defined as parent for common properties and behaviour.
 *
 * @param <T> Generic type argument for ActiveMQ messages consumed by this consumer implementation.
 *            Type T must be an instance of {@link Serializable}.
 */
public class ActiveMQConsumer<T extends Serializable>
		extends ActiveMQMessageHandler

{
	private static final Logger logger = LogManager.getLogger(ActiveMQConsumer.class);

	private MessageConsumer consumer;

	private Thread consumerThread;

	/**
	 * Refer following documentation for valid broker URL formats -
	 *
	 * 1. <a href="http://activemq.apache.org/configuring-version-5-transports.html">Configuring transports</a>
	 * 2. <a href="http://activemq.apache.org/failover-transport-reference.html">Failover transports</a>
	 *
	 * @param brokerURL A valid URL to connect to ActiveMQ broker
	 * @param queueName An alpha-numeric name of the ActiveMQ queue, on which consumer will be listening
	 */
	public ActiveMQConsumer(String brokerURL, String queueName)
	{
		super(brokerURL, queueName);
	}

	/**
	 * Initializes connection factory, connection, session, message queue and message consumer instances.
	 *
	 * @throws JMSException when -
	 *                      1. The broker URL format is incorrect
	 *                      2. The broker is started without 'failover' option and server is not running
	 *                      3. The connection factory is unable to create a connection
	 */
	protected void initialize() throws JMSException
	{
		super.initialize();

		// Creating consumer instance
		consumer = session.createConsumer(queue);

		/**
		 * A separate thread is required to continuously receive message from the message queue,
		 * without blocking application's main thread.
		 */
		// Creating thread for reading
		consumerThread = new Thread(this::run);
	}

	/**
	 * Initializes and starts the message consumer.
	 *
	 * @throws JMSException when initialization, of the message consumer, is not successful.
	 */
	public void start() throws JMSException
	{
		// Initializing consumer
		this.initialize();

		// Starting reading
		consumerThread.start();
	}

	/**
	 * Provides {@link Runnable} compatible behaviour for receiving new messages from the message queue.
	 * In case when the message queue is empty, the method waits for 5 seconds before trying to receive the next message.
	 *
	 * Method will return in case the consumerThread is interrupted explicitly or due to shutting down of the JVM.
	 */
	private void run()
	{
		while (!consumerThread.isInterrupted())
		{
			try
			{
				final Message message = consumer.receiveNoWait();

				if (message == null)
				{
					// No message in queue; waiting for message
					Thread.sleep(5000);
					continue;
				}

				if (message instanceof TextMessage)
				{
					final TextMessage textMessage = (TextMessage) message;
					logger.info("Received :: {}", textMessage.getText());
				}
			}
			catch (IllegalStateException e)
			{
				break;
			}
			catch (Exception e)
			{
				logger.error(e.getMessage(), e);
			}
		}
	}

	/**
	 * Tries to receive a message from the consumer without waiting for one in case of an empty queue.
	 *
	 * @return object of type T by transforming {@link Message} according to its underlying implementation.
	 *
	 * @throws JMSException when -
	 *                      1. The consumer or its corresponding session is closed.
	 *                      2. The broker is down and connection factory is not configured with 'failover' option.
	 */
	public T nextMessageNoWait() throws JMSException
	{
		return transform(consumer.receiveNoWait());
	}

	/**
	 * Tries to receive a message from the consumer, if one is readily available.
	 * If not, consumer will wait for the amount of time specified by the 'timeout' parameter.
	 *
	 * @param timeout in milliseconds for which a broker will wait for a message to arrive,
	 *                in case when one is not readily available in the queue.
	 *
	 * @return object of type T by transforming {@link Message} according to its underlying implementation.
	 *
	 * @throws JMSException when -
	 *                      1. The consumer or its corresponding session is closed.
	 *                      2. The broker is down and connection factory is not configured with 'failover' option.
	 */
	public T nextMessage(long timeout) throws JMSException
	{
		return transform(consumer.receive(timeout));
	}

	/**
	 * Transforms {@link Message} according to its underlying implementation.
	 * {@link TextMessage} is transformed into {@link String} object,
	 * while {@link ObjectMessage} is transformed to its embedded object.
	 *
	 * @param message to transformed.
	 *
	 * @return object of type T as the result of transformation of 'message' parameter.
	 *
	 * @throws JMSException when transformation is not possible.
	 */
	@SuppressWarnings("unchecked")
	private T transform(Message message) throws JMSException
	{
		if (message == null)
		{
			return null;
		}

		return (T) (message instanceof ActiveMQTextMessage ? ((ActiveMQTextMessage) message).getText() : ((ObjectMessage) message).getObject());
	}


	/**
	 * Closes all the resources held by this instance of message consumer.
	 */
	public void close()
	{
		JMSUtil.close(consumer);

		super.close();
	}
}
