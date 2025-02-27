package ca.concordia.encs.citydata.core;

import java.util.Date;

/***
 * This class is the helper method for generating report for producer call information.
 * @Author: Rushin Makwana
 * @Date: 26/2/2024
 */
public class ProducerCallInfo {
		private String user;
		private Date timestamp;
		private String requestBody;
		private String producerName;

		public ProducerCallInfo(String user, Date timestamp, String requestBody, String producerName) {
			this.user = user;
			this.timestamp = timestamp;
			this.requestBody = requestBody;
			this.producerName = producerName;
		}

		public String getUser() {
			return user;
		}

		public Date getTimestamp() {
			return timestamp;
		}

		public void setTimestamp(Date timestamp) {
			this.timestamp = timestamp;
		}

		public String getRequestBody() {
			return requestBody;
		}

		public String getProducerName() {
			return producerName;
		}
}
