/*
 * <<
 * Davinci
 * ==
 * Copyright (C) 2016 - 2017 EDP
 * ==
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * >>
 */

import React from 'react'
import classnames from 'classnames'
import { ICSVMetaInfo } from '../types'

import { Modal, Form, Row, Col, Input, Radio, Upload, Icon, Popover, Button, Steps } from 'antd'
const RadioGroup = Radio.Group
const Step = Steps.Step
const FormItem = Form.Item
import { FormComponentProps } from 'antd/lib/form/Form'
import { UploadProps } from 'antd/lib/upload/Upload'

const styles = require('../Source.less')

interface IUploadCsvFormProps {
  visible: boolean
  step: number
  uploadProps: UploadProps
  csvMeta: ICSVMetaInfo
  onStepChange: (step: number, values?: ICSVMetaInfo) => void
  onUpload: () => void
  onClose: () => void
  onAfterClose: () => void
}

export class UploadCsvForm extends React.PureComponent<IUploadCsvFormProps & FormComponentProps> {

  private commonFormItemStyle = {
    labelCol: { span: 6 },
    wrapperCol: { span: 16 }
  }

  public componentDidUpdate (prevProps: IUploadCsvFormProps & FormComponentProps) {
    const { form, csvMeta, visible } = this.props
    if (csvMeta !== prevProps.csvMeta || visible !== prevProps.visible) {
      form.setFieldsValue(csvMeta)
    }
  }

  private changeStep = (step: number) => () => {
    if (step) {
      this.props.form.validateFieldsAndScroll((err, values) => {
        if (!err) {
          this.props.onStepChange(step, values)
        }
      })
    } else {
      this.props.onStepChange(step)
    }
  }

  private reset = () => {
    const { form, onAfterClose } = this.props
    form.resetFields()
    onAfterClose()
  }

  public render () {
    const {
      visible,
      step,
      form,
      uploadProps,
      onUpload,
      onClose
    } = this.props
    const { getFieldDecorator } = form

    const baseInfoStyle = classnames({
      [styles.hide]: !!step
    })

    const authInfoStyle = classnames({
      [styles.hide]: !step
    })

    const submitDisabled = uploadProps.fileList.length <= 0 || uploadProps.fileList[0].status !== 'success'

    const modalButtons = step
      ? [(
      <Button
        key="submit"
        size="large"
        type="primary"
        disabled={submitDisabled}
        onClick={onUpload}
      >
          ??? ???
      </Button>)
      ]
      : [(
      <Button
        key="forward"
        size="large"
        type="primary"
        onClick={this.changeStep(1)}
      >
          ?????????
      </Button>)
      ]

    return (
      <Modal
        title="??????CSV"
        maskClosable={false}
        visible={visible}
        wrapClassName="ant-modal-small"
        footer={modalButtons}
        onCancel={onClose}
        afterClose={this.reset}
      >
        <Form>
          <Row className={styles.formStepArea}>
            <Col span={24}>
              <Steps current={step}>
                <Step title="????????????" />
                <Step title="??????CSV" />
                <Step title="??????" />
              </Steps>
            </Col>
          </Row>
          <Row gutter={8} className={baseInfoStyle}>
            <Col span={24}>
              <FormItem label="??????" {...this.commonFormItemStyle}>
                {getFieldDecorator<ICSVMetaInfo>('tableName', {
                  rules: [{
                    required: true,
                    message: '?????????????????????'
                  }]
                })(
                  <Input />
                )}
              </FormItem>
              <FormItem label="Source ID" className={styles.hide}>
                {getFieldDecorator<ICSVMetaInfo>('sourceId')(
                  <Input />
                )}
              </FormItem>
            </Col>
            <Col span={24}>
              <FormItem label="??????" {...this.commonFormItemStyle}>
                {getFieldDecorator<ICSVMetaInfo>('primaryKeys', {
                })(
                  <Input />
                )}
              </FormItem>
            </Col>
            <Col span={24}>
              <FormItem label="?????????" {...this.commonFormItemStyle}>
                {getFieldDecorator<ICSVMetaInfo>('indexKeys', {
                })(
                  <Input />
                )}
              </FormItem>
            </Col>
            <Col span={24}>
              <FormItem label="????????????" {...this.commonFormItemStyle}>
                {getFieldDecorator<ICSVMetaInfo>('replaceMode', {
                  initialValue: 0
                })(
                  <RadioGroup>
                    <Radio value={0}>??????</Radio>
                    <Radio value={1}>??????</Radio>
                    <Radio value={2}>??????</Radio>
                  </RadioGroup>
                )}
                <Popover
                  placement="right"
                  content={
                    <p>????????????????????????????????????"??????"</p>
                  }
                >
                  <Icon type="question-circle-o" />
                </Popover>
              </FormItem>
            </Col>
          </Row>
          <Row className={authInfoStyle}>
            <Col span={24}>
              <FormItem
                {...this.commonFormItemStyle}
                label="??????"
              >
                <Upload {...uploadProps} >
                  <Button>
                    <Icon type="upload" />Click to Upload CSV
                  </Button>
                </Upload>
              </FormItem>
            </Col>
          </Row>
        </Form>
      </Modal>
    )
  }
}

export default Form.create<IUploadCsvFormProps & FormComponentProps>()(UploadCsvForm)

