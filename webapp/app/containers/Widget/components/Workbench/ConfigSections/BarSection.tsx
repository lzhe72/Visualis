import React from 'react'
import { Row, Col, Modal, Icon, InputNumber, Select, Checkbox } from 'antd'
const Option = Select.Option
import { CheckboxChangeEvent } from 'antd/lib/checkbox'
import ColorPicker from 'components/ColorPicker'
import { PIVOT_CHART_LINE_STYLES } from 'app/globalConstants'
import { getAggregatorLocale, decodeMetricName } from '../../util'
import { IBarConfig } from './'
import { IDataParams } from '../OperatingPanel'

import { StackConfigModal, StackMetrics, StackConfig } from '../../Config/Stack'

const styles = require('../Workbench.less')

interface IBarSectionProps {
  dataParams: IDataParams
  config: IBarConfig
  onChange: (
    prop: string,
    value: number | string | boolean | StackConfig,
    propPath: string[]
  ) => void
}

interface IBarSectionStates {
  stackConfigVisible: boolean
  stackMetrics: StackMetrics
}

export class BarSection extends React.PureComponent<
  IBarSectionProps,
  IBarSectionStates
> {
  constructor (props: IBarSectionProps) {
    super(props)
    const { dataParams } = props
    this.state = {
      stackMetrics: this.getStackMetrics(dataParams),
      stackConfigVisible: false
    }
  }

  public componentDidUpdate (prevProps: IBarSectionProps) {
    const { dataParams } = this.props
    if (dataParams !== prevProps.dataParams) {
      this.setState({
        stackMetrics: this.getStackMetrics(dataParams)
      })
    }
  }

  private lineStyles = PIVOT_CHART_LINE_STYLES.map((l) => (
    <Option key={l.value} value={l.value}>
      {l.name}
    </Option>
  ))

  private propChange = (propName: string, propPath?: string) => (
    e: number | string | CheckboxChangeEvent
  ) => {
    const value = (e as CheckboxChangeEvent).target
      ? (e as CheckboxChangeEvent).target.checked
      : (e as string | number)
    this.props.onChange(propName, value, propPath ? [propPath] : [])
  }

  private getStackMetrics = (dataParams: IDataParams) => {
    const metrics = dataParams['metrics'].items.reduce((acc, { name, agg }) => {
      acc[name] = `[${getAggregatorLocale(agg)}]${decodeMetricName(name)}`
      return acc
    }, {})
    return metrics
  }

  private deleteStackConfig = () => {
    const { onChange } = this.props
    Modal.confirm({
      title: '???????????????????????????',
      onOk: () => {
        onChange('stack', undefined, [])
      }
    })
  }

  private showStackConfig = () => {
    this.setState({ stackConfigVisible: true })
  }

  private cancelStackConfig = () => {
    this.setState({ stackConfigVisible: false })
  }

  private saveStackConfig = (config: StackConfig) => {
    this.setState({ stackConfigVisible: false })
    this.props.onChange('stack', config, [])
  }

  public render () {
    const { config } = this.props
    const { barChart, border, gap, width: gapWidth, stack } = config
    const { color, width, type, radius } = border
    const { stackConfigVisible, stackMetrics } = this.state

    return (
      <>
        <div className={styles.paneBlock}>
          <h4>
            <span>??????</span>
            <Icon type="delete" onClick={this.deleteStackConfig} />
            <Icon type="edit" onClick={this.showStackConfig} />
          </h4>
        </div>
        <div className={styles.paneBlock}>
          <div className={styles.blockBody}>
            <Row
              gutter={8}
              type="flex"
              align="middle"
              className={styles.blockRow}
            >
              <Col span={24}>
                <Checkbox
                  checked={barChart}
                  onChange={this.propChange('barChart')}
                >
                  ?????????
                </Checkbox>
              </Col>
            </Row>
            <Row
              gutter={8}
              type="flex"
              align="middle"
              className={styles.blockRow}
            >
              <Col span={10}>
                <Select
                  placeholder="??????"
                  className={styles.blockElm}
                  value={type}
                  onChange={this.propChange('type', 'border')}
                >
                  {this.lineStyles}
                </Select>
              </Col>
              <Col span={10}>
                <InputNumber
                  placeholder="??????"
                  className={styles.blockElm}
                  value={width}
                  min={0}
                  onChange={this.propChange('width', 'border')}
                />
              </Col>
              <Col span={4}>
                <ColorPicker
                  value={color}
                  onChange={this.propChange('color', 'border')}
                />
              </Col>
            </Row>
            <Row
              gutter={8}
              type="flex"
              align="middle"
              className={styles.blockRow}
            >
              <Col span={14}>????????????</Col>
              <Col span={10}>
                <InputNumber
                  className={styles.blockElm}
                  value={radius}
                  min={0}
                  onChange={this.propChange('radius', 'border')}
                />
              </Col>
            </Row>
            <Row
              gutter={8}
              type="flex"
              align="middle"
              className={styles.blockRow}
            >
              <Col span={14}>????????????</Col>
              <Col span={10}>
                <InputNumber
                  className={styles.blockElm}
                  value={gapWidth}
                  onChange={this.propChange('width')}
                />
              </Col>
            </Row>
            <Row
              gutter={8}
              type="flex"
              align="middle"
              className={styles.blockRow}
            >
              <Col span={14}>???????????????????????????</Col>
              <Col span={10}>
                <InputNumber
                  className={styles.blockElm}
                  value={gap}
                  onChange={this.propChange('gap')}
                />
              </Col>
            </Row>
          </div>
        </div>
        <StackConfigModal
          visible={stackConfigVisible}
          stack={stack}
          metrics={stackMetrics}
          onCancel={this.cancelStackConfig}
          onSave={this.saveStackConfig}
        />
      </>
    )
  }
}

export default BarSection
